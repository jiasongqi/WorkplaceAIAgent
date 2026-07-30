package com.yupi.yuaiagent.rag;

import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceContextHolder;
import com.yupi.yuaiagent.trace.TraceRecorder;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RAG 查询重写器：额外一次同步 LLM 调用，提升检索词质量，但会拉高 TTFT。
 * <p>
 * 可通过 {@code rag.query-rewrite.enabled=false} 短路对比；
 * {@code rag.query-rewrite.timeout-ms} 控制单次上限，超时 fail-open 回退原文。
 */
@Slf4j
@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;
    private final Executor rewriteExecutor;

    @Value("${rag.query-rewrite.enabled:true}")
    private boolean enabled;

    @Value("${rag.query-rewrite.timeout-ms:8000}")
    private long timeoutMs;

    @Resource
    private TraceRecorder traceRecorder;

    public QueryRewriter(ChatModel dashscopeChatModel,
                         @Qualifier("agentExecutor") Executor rewriteExecutor) {
        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
        this.rewriteExecutor = rewriteExecutor;
    }

    /**
     * 执行查询重写；关闭/超时/异常时返回原文（fail-open）。
     */
    public String doQueryRewrite(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return prompt == null ? "" : prompt;
        }

        TraceContext ctx = TraceContextHolder.get();
        TraceSpan span = traceRecorder != null
                ? traceRecorder.startSpan(ctx, TraceStepType.QUERY_REWRITE, "查询重写")
                : null;

        if (!enabled) {
            log.info("[QueryRewrite] skipped (rag.query-rewrite.enabled=false)");
            if (traceRecorder != null && span != null) {
                traceRecorder.putMetadata(span, "skipped", "true");
                traceRecorder.putMetadata(span, "reason", "disabled");
                traceRecorder.skipSpan(ctx, span);
            }
            return prompt;
        }

        long start = System.currentTimeMillis();
        try {
            String rewritten = CompletableFuture
                    .supplyAsync(() -> transform(prompt), rewriteExecutor)
                    .orTimeout(Math.max(500L, timeoutMs), TimeUnit.MILLISECONDS)
                    .join();

            long ms = System.currentTimeMillis() - start;
            boolean changed = rewritten != null && !rewritten.equals(prompt);
            log.info("[QueryRewrite] ok {}ms changed={} inLen={} outLen={}",
                    ms, changed, prompt.length(), rewritten != null ? rewritten.length() : 0);

            if (traceRecorder != null && span != null) {
                traceRecorder.putMetadata(span, "latencyMs", String.valueOf(ms));
                traceRecorder.putMetadata(span, "changed", String.valueOf(changed));
                traceRecorder.putMetadata(span, "timeoutMs", String.valueOf(timeoutMs));
                traceRecorder.endSpan(ctx, span);
            }
            return StringUtils.hasText(rewritten) ? rewritten : prompt;
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - start;
            Throwable cause = unwrap(e);
            String reason = cause instanceof TimeoutException
                    ? "timeout>" + timeoutMs + "ms"
                    : cause.getClass().getSimpleName() + ": " + cause.getMessage();
            log.warn("[QueryRewrite] fail-open after {}ms — {} (using original query)", ms, reason);

            if (traceRecorder != null && span != null) {
                traceRecorder.putMetadata(span, "latencyMs", String.valueOf(ms));
                traceRecorder.putMetadata(span, "failOpen", "true");
                traceRecorder.putMetadata(span, "reason", reason);
                // Fail-open is intentional recovery, not a hard failure for the turn.
                traceRecorder.skipSpan(ctx, span);
            }
            return prompt;
        }
    }

    private String transform(String prompt) {
        Query transformed = queryTransformer.transform(new Query(prompt));
        return transformed != null ? transformed.text() : prompt;
    }

    private static Throwable unwrap(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur != cur.getCause()
                && (cur instanceof java.util.concurrent.CompletionException
                || cur instanceof java.util.concurrent.ExecutionException)) {
            cur = cur.getCause();
        }
        return cur;
    }
}
