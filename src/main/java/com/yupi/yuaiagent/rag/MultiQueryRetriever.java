package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-Query 多路召回检索器
 * 将用户问题扩展为多个变体，分别检索后合并去重
 *
 * @author jsq
 */
@Slf4j
public class MultiQueryRetriever {

    private final VectorStore vectorStore;
    private final int topKPerQuery;
    private final QueryRewriter queryRewriter;

    public MultiQueryRetriever(VectorStore vectorStore, QueryRewriter queryRewriter) {
        this(vectorStore, queryRewriter, 3);
    }

    public MultiQueryRetriever(VectorStore vectorStore, QueryRewriter queryRewriter, int topKPerQuery) {
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
        this.topKPerQuery = topKPerQuery;
    }

    /**
     * 执行 Multi-Query 检索
     *
     * @param query          原始查询（用于日志）
     * @param expandedQueries 扩展后的查询列表
     * @return 合并去重后的文档列表
     */
    public List<Document> retrieve(String query, List<Query> expandedQueries) {
        Set<String> seen = new LinkedHashSet<>();
        List<Document> allDocs = new ArrayList<>();

        for (Query q : expandedQueries) {
            try {
                List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(q.text())
                                .topK(topKPerQuery)
                                .build());

                for (Document doc : docs) {
                    String content = doc.getText();
                    if (seen.add(content)) {
                        allDocs.add(doc);
                    }
                }
            } catch (Exception e) {
                log.warn("Multi-Query 变体检索失败：{}", e.getMessage());
            }
        }

        log.info("Multi-Query 检索完成：{} 个变体，合并后 {} 个唯一文档", expandedQueries.size(), allDocs.size());
        return allDocs;
    }

    /**
     * 将文档列表合并为上下文字符串
     *
     * @param documents 文档列表
     * @return 合并后的上下文
     */
    public String buildContext(List<Document> documents) {
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 完整的 Multi-Query 检索流程：重写 -> 扩展 -> 检索 -> 合并
     * 使用项目中已有的 MultiQueryExpander
     *
     * @param originalQuery 原始查询
     * @param expander      查询扩展器（MultiQueryExpander）
     * @return 检索结果
     */
    public RetrievalResult retrieveWithRewrite(String originalQuery, MultiQueryExpander expander) {
        // 1. 查询重写
        String rewrittenQuery = queryRewriter.doQueryRewrite(originalQuery);
        log.info("查询重写：{} -> {}", originalQuery, rewrittenQuery);

        // 2. 查询扩展
        List<Query> expandedQueries = expander.expand(new Query(rewrittenQuery));
        log.info("查询扩展为 {} 个变体", expandedQueries.size());

        // 3. 多路检索
        List<Document> documents = retrieve(rewrittenQuery, expandedQueries);

        // 4. 构建上下文
        String context = buildContext(documents);

        return new RetrievalResult(rewrittenQuery, expandedQueries, documents, context);
    }

    /**
     * 检索结果
     */
    public record RetrievalResult(
            String rewrittenQuery,
            List<Query> expandedQueries,
            List<Document> documents,
            String context
    ) {
        public boolean hasContext() {
            return context != null && !context.isEmpty();
        }

        public String buildPrompt(String userQuestion) {
            if (!hasContext()) {
                return userQuestion;
            }
            return "请基于以下参考资料回答用户问题：\n\n" + context + "\n\n用户问题：" + userQuestion;
        }
    }
}
