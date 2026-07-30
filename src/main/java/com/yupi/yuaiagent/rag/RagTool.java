package com.yupi.yuaiagent.rag;

import com.yupi.yuaiagent.hitl.AgentRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * RAG Tool — decoupled knowledge retrieval for any Agent via {@link RetrievalPipeline}.
 *
 * <p>Pipeline: query rewrite → vector recall → rerank → formatted snippets with source refs.</p>
 */
@Slf4j
public class RagTool {

    private static final String EMPTY_HINT =
            "未找到相关文档。请尝试换个关键词或上传相关文档。";
    private static final String BLOCKED_HINT_TEMPLATE =
            "已连续 %d 次未在知识库中找到相关信息。请向用户询问更具体的关键词或引导上传文档，勿再重复检索。";

    private final RetrievalPipeline retrievalPipeline;
    private final RagRetrievalAttemptTracker attemptTracker;

    public RagTool(RetrievalPipeline retrievalPipeline, RagRetrievalAttemptTracker attemptTracker) {
        this.retrievalPipeline = retrievalPipeline;
        this.attemptTracker = attemptTracker;
    }

    /**
     * Search knowledge base with a query.
     */
    public String search(String query, int topK, String filter, boolean useHyDE) {
        String chatId = AgentRequestContext.chatId();
        if (attemptTracker.shouldBlock(chatId)) {
            return String.format(BLOCKED_HINT_TEMPLATE, attemptTracker.maxEmptyRetries());
        }

        RetrievalOptions base = RetrievalOptions.toolDefaults();
        RetrievalOptions options = new RetrievalOptions(
                filter,
                topK > 0 ? topK : base.topK(),
                false,
                useHyDE,
                base.similarityThreshold()
        );

        try {
            RetrievalPipeline.RetrievalResult result = retrievalPipeline.retrieve(query, options);
            if (!result.hasHits()) {
                attemptTracker.recordEmpty(chatId);
                return EMPTY_HINT;
            }
            attemptTracker.recordSuccess(chatId);
            return result.formattedResults();
        } catch (Exception e) {
            log.error("[RagTool] Search failed: {}", e.getMessage());
            return "知识库检索失败：" + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(description = """
            Search the internal knowledge base (uploaded career/job documents) via vector retrieval.
            WHEN TO USE: user asks about content that may already be in the knowledge base — resumes, interview FAQs, salary notes, resignation templates.
            DO NOT USE: live web facts (use searchWeb); a concrete public URL (use scrapeWebPage); reading a local sandbox file (use readFile / readFileChunk).
            RETURNS: top snippets with optional filename source. Read-only; safe to retry unless output says stop retrying.""")
    public String searchKnowledgeBase(
            @org.springframework.ai.tool.annotation.ToolParam(description = "Short retrieval query; do not paste entire documents") String query) {
        if (!StringUtils.hasText(query)) {
            return EMPTY_HINT;
        }
        return search(query, 0, null, false);
    }

    public record RagToolInput(String query, int topK, String filter, boolean useHyDE) {}
}
