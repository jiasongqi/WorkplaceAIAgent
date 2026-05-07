package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.agent.YuManus;
import com.yupi.yuaiagent.app.AiChatAgent;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiChatAgent aiChatAgent;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    /**
     * 同步调用 AI 职场顾问应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/ai_chat/chat/sync")
    public String doChatWithAiChatSync(String message, String chatId) {
        return aiChatAgent.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 职场顾问应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/ai_chat/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithAiChatSSE(String message, String chatId) {
        return aiChatAgent.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 职场顾问应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/ai_chat/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithAiChatServerSentEvent(String message, String chatId) {
        return aiChatAgent.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 职场顾问应用
     *
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/ai_chat/chat/sse_emitter")
    public SseEmitter doChatWithAiChatServerSseEmitter(String message, String chatId) {
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(180000L); // 3 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        aiChatAgent.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param message
     * @return
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        YuManus yuManus = new YuManus(allTools, dashscopeChatModel);
        return yuManus.runStream(message);
    }
}
