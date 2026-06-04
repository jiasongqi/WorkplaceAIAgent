package com.yupi.yuaiagent.profile;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.profile.model.CommunicationPreference;
import com.yupi.yuaiagent.profile.model.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户画像抽取器
 * <p>
 * 在对话结束后，基于本次对话消息调用 LLM 抽取用户画像维度
 * （沟通偏好、语气偏好、关注领域、已知背景、历史诉求），并返回 {@link UserProfile} 对象。
 * 抽取结果交由上层 {@code UserProfileService} 合并到已有画像。
 * <p>
 * 容错策略：当对话内容为空、LLM 调用失败或返回无法解析的内容时，
 * 不抛出异常打断主流程，而是返回一个不含任何维度的空 {@link UserProfile}（各列表为空、各标量为 null），
 * 合并到已有画像时不会覆盖或污染原画像。
 *
 * @author jsq
 */
@Slf4j
@Component
public class UserProfileExtractor {

    /**
     * 画像抽取系统提示词。要求 LLM 仅输出结构化数据，未识别到的维度留空。
     */
    private static final String EXTRACT_SYSTEM_PROMPT = """
            你是一名用户画像分析专家。请基于下面提供的用户与助手的对话内容，
            客观抽取出该用户的画像维度。仅依据对话中有明确依据的信息进行抽取，
            没有依据的维度请留空（字符串留空、列表返回空数组），不要编造。

            需要抽取的维度：
            1. communicationPreference：用户的沟通偏好，仅能取 "CONCISE"（偏好简洁回答）或 "DETAILED"（偏好详细回答）之一；无法判断时留空。
            2. tonePreference：用户偏好的语气风格（如 鼓励型、直接型、专业严谨 等），用简短词语描述；无法判断时留空。
            3. focusAreas：用户关注的领域或话题列表（如 简历优化、薪资谈判、晋升规划 等）。
            4. knownBackground：用户的已知背景信息（如 所在行业、岗位、工作年限 等），用一句话概括；无法判断时留空。
            5. historicalDemands：用户在本次对话中表达的核心诉求列表（如 想跳槽、希望加薪 等）。

            对话内容：
            {conversation}
            """;

    private final ChatClient chatClient;

    public UserProfileExtractor(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }

    /**
     * 基于对话消息抽取用户画像。
     *
     * @param conversation 本次对话的消息列表，可能为 null 或空
     * @return 抽取到的用户画像；当输入为空或抽取失败时返回不含任何维度的空画像（绝不返回 null）
     */
    public UserProfile extract(List<Message> conversation) {
        String conversationText = formatConversation(conversation);
        if (!StringUtils.hasText(conversationText)) {
            log.info("对话内容为空，跳过用户画像抽取");
            return emptyProfile();
        }

        try {
            ExtractedProfile extracted = chatClient.prompt()
                    .system(EXTRACT_SYSTEM_PROMPT.replace("{conversation}", conversationText))
                    .user("请抽取上述对话中的用户画像维度。")
                    .call()
                    .entity(ExtractedProfile.class);
            return toUserProfile(extracted);
        } catch (Exception e) {
            // 抽取失败时优雅降级：返回空画像，避免打断对话主流程
            log.error("用户画像抽取失败，返回空画像", e);
            return emptyProfile();
        }
    }

    /**
     * 将对话消息列表格式化为带角色前缀的纯文本。
     *
     * @param conversation 消息列表
     * @return 格式化后的对话文本；无有效内容时返回空字符串
     */
    private String formatConversation(List<Message> conversation) {
        if (CollectionUtils.isEmpty(conversation)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message message : conversation) {
            if (message == null) {
                continue;
            }
            String content = message.getText();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            sb.append(roleName(message)).append("：").append(content).append("\n");
        }
        return sb.toString();
    }

    /**
     * 根据消息类型返回中文角色名称。
     */
    private String roleName(Message message) {
        if (message instanceof UserMessage) {
            return "用户";
        } else if (message instanceof AssistantMessage) {
            return "助手";
        } else if (message instanceof SystemMessage) {
            return "系统";
        }
        return "未知";
    }

    /**
     * 将 LLM 抽取结果映射为 {@link UserProfile}，对沟通偏好做安全解析。
     */
    private UserProfile toUserProfile(ExtractedProfile extracted) {
        if (extracted == null) {
            return emptyProfile();
        }
        return UserProfile.builder()
                .communicationPreference(parsePreference(extracted.communicationPreference()))
                .tonePreference(blankToNull(extracted.tonePreference()))
                .knownBackground(blankToNull(extracted.knownBackground()))
                .focusAreas(cleanList(extracted.focusAreas()))
                .historicalDemands(cleanList(extracted.historicalDemands()))
                .build();
    }

    /**
     * 安全解析沟通偏好枚举，无法识别时返回 null。
     */
    private CommunicationPreference parsePreference(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return CommunicationPreference.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无法识别的沟通偏好取值：{}", raw);
            return null;
        }
    }

    /**
     * 清洗字符串列表：剔除 null 与空白条目并去除首尾空白；空列表返回空 ArrayList。
     */
    private List<String> cleanList(List<String> source) {
        List<String> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(source)) {
            return result;
        }
        for (String item : source) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    /**
     * 空白字符串归一化为 null。
     */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 构建不含任何维度的空画像（各列表为空、各标量为 null）。
     */
    private UserProfile emptyProfile() {
        return UserProfile.builder()
                .focusAreas(new ArrayList<>())
                .historicalDemands(new ArrayList<>())
                .build();
    }

    /**
     * LLM 结构化抽取的中间载体。
     * 沟通偏好以字符串承载以便做安全解析，避免 LLM 返回非法枚举值导致反序列化失败。
     *
     * @param communicationPreference 沟通偏好原始字符串（CONCISE / DETAILED / 空）
     * @param tonePreference          语气偏好
     * @param focusAreas              关注领域列表
     * @param knownBackground         已知背景
     * @param historicalDemands       历史诉求列表
     */
    public record ExtractedProfile(
            String communicationPreference,
            String tonePreference,
            List<String> focusAreas,
            String knownBackground,
            List<String> historicalDemands
    ) {
    }
}
