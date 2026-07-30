package com.yupi.yuaiagent.perception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Lightweight image caption — filename/hint heuristics + optional text LLM guess (Ch5 Captioning).
 */
@Slf4j
@Service
public class ImageCaptionService {

    private final ChatClient chatClient;
    private final boolean llmGuessEnabled;

    public ImageCaptionService(@Qualifier("dashscopeChatModel") ChatModel chatModel,
                               @Value("${perception.image-caption.llm-guess-enabled:true}") boolean llmGuessEnabled) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.llmGuessEnabled = llmGuessEnabled;
    }

    public String caption(byte[] imageBytes, String filename, String hint) {
        if (llmGuessEnabled) {
            try {
                String guessed = chatClient.prompt()
                        .user("""
                                用户上传了一张职场相关图片（未直接传图，仅知文件名与场景）。
                                文件名：%s
                                场景提示：%s
                                请用一句中文推测图片可能内容（≤80字），并注明「推测」。
                                """.formatted(
                                StringUtils.hasText(filename) ? filename : "unknown",
                                StringUtils.hasText(hint) ? hint : "general"))
                        .call()
                        .content();
                if (StringUtils.hasText(guessed)) {
                    return guessed.trim();
                }
            } catch (Exception e) {
                log.warn("[ImageCaption] LLM guess failed: {}", e.getMessage());
            }
        }
        return heuristicCaption(filename, hint);
    }

    private static String heuristicCaption(String filename, String hint) {
        String base = StringUtils.hasText(filename) ? filename : "图片";
        if ("offer".equalsIgnoreCase(hint)) {
            return "Offer/薪资相关图片：" + base;
        }
        if ("resume".equalsIgnoreCase(hint)) {
            return "简历相关图片：" + base;
        }
        return "用户上传的职场材料图片：" + base;
    }
}
