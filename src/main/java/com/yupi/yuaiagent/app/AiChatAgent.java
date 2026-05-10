package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.demo.rag.MultiQueryExpanderDemo;
import com.yupi.yuaiagent.rag.MultiQueryRetriever;
import com.yupi.yuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.Query;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 对话 Agent
 * 
 * 优化点：
 * 1. 使用 ChatMemoryManager 统一管理会话记忆
 * 2. 使用 MultiQueryRetriever 封装 Multi-Query 检索逻辑
 */
@Component
@Slf4j
public class AiChatAgent {

    private final ChatClient chatClient;
    private final ChatModel chatModel;
    private final ChatMemory chatMemory;

    private static final String SYSTEM_PROMPT = "扮演深耕职场领域的专家顾问。开场向用户表明身份，告知用户可倾诉职场困惑与挑战。" +
            "围绕求职、在职、晋升三种状态提问：求职状态询问简历优化、面试技巧及 offer 选择的困扰；" +
            "在职状态询问同事关系、工作效率及与上级沟通的矛盾；晋升状态询问晋升规划、薪资谈判及角色转型的问题。" +
            "引导用户详述事情经过、相关方反应及自身想法，以便给出专属职场解决方案。";

    public AiChatAgent(ChatModel dashscopeChatModel, ChatMemoryManager chatMemoryManager) {
        this.chatModel = dashscopeChatModel;
        // 使用 ChatMemoryManager 获取共享的 ChatMemory
        this.chatMemory = chatMemoryManager.getMemory("general");
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（流式）
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    public record AiChatReport(String title, List<String> suggestions) {
    }

    /**
     * AI 职场报告（结构化输出）
     */
    public AiChatReport doChatWithReport(String message, String chatId) {
        AiChatReport aiChatReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成职场分析结果，标题为{用户名}的职场报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(AiChatReport.class);
        log.info("aiChatReport: {}", aiChatReport);
        return aiChatReport;
    }

    // RAG 知识库问答

    @Resource
    private VectorStore aiChatVectorStore;

    @Resource
    private Advisor aiChatRagCloudAdvisor;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private MultiQueryExpanderDemo multiQueryExpanderDemo;

    /**
     * 和 RAG 知识库进行对话（含 Multi-Query 多路召回）
     * 
     * 优化：使用 MultiQueryRetriever 封装检索逻辑
     */
    public String doChatWithRag(String message, String chatId) {
        // 1. 创建 MultiQueryRetriever
        MultiQueryRetriever retriever = new MultiQueryRetriever(aiChatVectorStore, queryRewriter);
        
        // 2. 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        log.info("查询重写：{} -> {}", message, rewrittenMessage);
        
        // 3. Multi-Query 扩展
        List<Query> expandedQueries = multiQueryExpanderDemo.expand(rewrittenMessage);
        log.info("Multi-Query 扩展结果（共 {} 个变体）", expandedQueries.size());
        
        // 4. 多路检索并合并结果
        List<org.springframework.ai.document.Document> documents = retriever.retrieve(rewrittenMessage, expandedQueries);
        log.info("Multi-Query 合并后共 {} 个唯一文档片段", documents.size());
        
        // 5. 构建带上下文的 prompt
        String context = retriever.buildContext(documents);
        String contextPrompt = context.isEmpty()
                ? rewrittenMessage
                : "请基于以下参考资料回答用户问题：\n\n" + context + "\n\n用户问题：" + rewrittenMessage;

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(contextPrompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // 工具调用

    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 对话（支持工具调用）
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    // MCP 服务

    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 对话（调用 MCP 服务）
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
