package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.collaboration.ExpertOpinion;
import com.yupi.yuaiagent.agent.output.FormatterRegistry;
import com.yupi.yuaiagent.agent.task.ExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates Agent execution results into a final answer.
 * <p>
 * Phase 1: structured concatenation.
 * Phase 2: optional LLM synthesis for parallel debate / vote.
 */
@Slf4j
@Component
public class ResultAggregator {

    private final FormatterRegistry formatterRegistry;
    private final ChatModel chatModel;

    public ResultAggregator(FormatterRegistry formatterRegistry,
                            @Autowired(required = false) ChatModel chatModel) {
        this.formatterRegistry = formatterRegistry;
        this.chatModel = chatModel;
    }

    /**
     * Aggregate TaskExecutor results into a single formatted response.
     */
    public Flux<String> aggregate(String question, List<ExecutionResult> results) {
        String formatted = results.stream()
            .filter(ExecutionResult::isSuccess)
            .map(r -> formatterRegistry.format(r.output()))
            .collect(Collectors.joining("\n\n"));

        String skipped = results.stream()
            .filter(ExecutionResult::isSkipped)
            .map(r -> r.agentId() + "（已跳过）")
            .collect(Collectors.joining("、"));

        StringBuilder sb = new StringBuilder(formatted);
        if (!skipped.isEmpty()) {
            sb.append("\n\n（以下专家未参与：").append(skipped).append("）");
        }

        return Flux.just(sb.toString());
    }

    /**
     * Synthesize parallel expert opinions into one user-facing answer.
     * Uses LLM when available; falls back to structured merge + majority preference by length/completeness.
     */
    public String synthesizeDebate(String question, List<ExpertOpinion> opinions) {
        if (opinions == null || opinions.isEmpty()) {
            return "";
        }
        List<ExpertOpinion> ok = opinions.stream().filter(ExpertOpinion::success).toList();
        if (ok.isEmpty()) {
            return "";
        }
        if (ok.size() == 1) {
            return ok.get(0).answer();
        }

        if (chatModel != null) {
            try {
                StringBuilder expertBlock = new StringBuilder();
                for (ExpertOpinion o : ok) {
                    expertBlock.append("【").append(o.intent().getAgentName()).append("】\n")
                            .append(o.answer()).append("\n\n");
                }
                String prompt = """
                        你是多智能体协作的「综合裁决官」。多个职场专家已并行给出意见，请综合成一份对用户友好的最终回答。
                        
                        规则：
                        1. 保留各专家的关键可执行建议，消除重复
                        2. 若专家意见冲突，说明分歧并给出倾向性建议（相当于加权投票）
                        3. 用清晰小标题组织，控制在合理篇幅
                        4. 不要提及「作为裁决官」等元话语
                        
                        用户问题：
                        %s
                        
                        专家意见：
                        %s
                        """.formatted(question, expertBlock);

                String synthesized = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                if (StringUtils.hasText(synthesized)) {
                    log.info("[ResultAggregator] LLM synthesis ok, experts={}, chars={}",
                            ok.size(), synthesized.length());
                    return synthesized.trim();
                }
            } catch (Exception e) {
                log.warn("[ResultAggregator] LLM synthesis failed, fallback to merge: {}", e.getMessage());
            }
        }

        return mergeWithoutLlm(ok);
    }

    /**
     * Non-LLM merge: label each expert + pick longest as "primary vote" lead.
     */
    String mergeWithoutLlm(List<ExpertOpinion> ok) {
        ExpertOpinion lead = ok.stream()
                .max((a, b) -> Integer.compare(
                        a.answer() != null ? a.answer().length() : 0,
                        b.answer() != null ? b.answer().length() : 0))
                .orElse(ok.get(0));

        StringBuilder sb = new StringBuilder();
        sb.append("## 综合建议（多专家并行）\n\n");
        sb.append("以下由 ").append(ok.size()).append(" 位专家并行给出意见后合并；")
                .append("以「").append(lead.intent().getAgentName()).append("」为主线。\n\n");
        for (ExpertOpinion o : ok) {
            sb.append("### ").append(o.intent().getAgentName());
            if (o.intent() == lead.intent()) {
                sb.append("（主投票）");
            }
            sb.append("\n").append(o.answer()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
