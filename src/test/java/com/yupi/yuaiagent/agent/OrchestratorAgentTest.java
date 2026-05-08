package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
@Slf4j
class OrchestratorAgentTest {

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private VectorStore aiChatVectorStore;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private QueryRewriter queryRewriter;

    private OrchestratorAgent orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new OrchestratorAgent(dashscopeChatModel, aiChatVectorStore, allTools, queryRewriter);
    }

    /**
     * 测试意图路由到 ResumeAgent（简历/求职类问题）
     */
    @Test
    void testRouteToResumeAgent() {
        String chatId = UUID.randomUUID().toString();
        String message = "我的简历投了很多公司都没有回音，帮我分析一下简历应该怎么优化？";
        String answer = orchestrator.chat(message, chatId);
        log.info("=== ResumeAgent 回答 ===\n{}", answer);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
    }

    /**
     * 测试意图路由到 NegotiationAgent（薪资谈判类问题）
     */
    @Test
    void testRouteToNegotiationAgent() {
        String chatId = UUID.randomUUID().toString();
        String message = "我工作三年了，想跟公司谈涨薪 30%，应该怎么开口？";
        String answer = orchestrator.chat(message, chatId);
        log.info("=== NegotiationAgent 回答 ===\n{}", answer);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
    }

    /**
     * 测试意图路由到 EscapeAgent（离职类问题）
     */
    @Test
    void testRouteToEscapeAgent() {
        String chatId = UUID.randomUUID().toString();
        String message = "公司突然通知我被裁员了，我应该怎么维权，离职补偿怎么谈？";
        String answer = orchestrator.chat(message, chatId);
        log.info("=== EscapeAgent 回答 ===\n{}", answer);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
    }

    /**
     * 测试意图路由到 GeneralAgent（通用职场问题）
     */
    @Test
    void testRouteToGeneralAgent() {
        String chatId = UUID.randomUUID().toString();
        String message = "我和同事关系很差，总是被排挤，怎么改善职场人际关系？";
        String answer = orchestrator.chat(message, chatId);
        log.info("=== GeneralAgent 回答 ===\n{}", answer);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
    }

    /**
     * 测试多轮对话：同一 chatId 下连续提问，验证上下文连贯性
     */
    @Test
    void testMultiTurnChat() {
        String chatId = UUID.randomUUID().toString();

        String answer1 = orchestrator.chat("我叫小明，是一名 Java 后端开发，工作了两年", chatId);
        log.info("=== 第一轮 ===\n{}", answer1);
        Assertions.assertNotNull(answer1);

        String answer2 = orchestrator.chat("我想跳槽，简历应该怎么写？", chatId);
        log.info("=== 第二轮（路由到 ResumeAgent）===\n{}", answer2);
        Assertions.assertNotNull(answer2);
    }

    /**
     * 测试边界情况：模糊问题，应降级到 GENERAL 路由
     */
    @Test
    void testFallbackToGeneral() {
        String chatId = UUID.randomUUID().toString();
        String message = "我最近工作压力很大，感觉很迷茫";
        String answer = orchestrator.chat(message, chatId);
        log.info("=== Fallback GENERAL 回答 ===\n{}", answer);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
    }
}
