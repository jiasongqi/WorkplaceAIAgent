package com.yupi.yuaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Fact-Preserving Compressor — compresses conversation history while preserving key facts.
 *
 * <p>Standard compression may lose user-stated facts like "我叫小琪" or "我的手机号是18104620109".
 * This compressor identifies and preserves such facts across compression boundaries.</p>
 *
 * <p>Fact patterns preserved:</p>
 * <ul>
 *     <li>Name: "我叫X", "我是X", "我的名字是X"</li>
 *     <li>Contact: phone numbers, emails</li>
 *     <li>Company: "我在X公司", "我工作的公司是X"</li>
 *     <li>Position: "我是X岗位", "我的职位是X"</li>
 *     <li>Preferences: "我喜欢/偏好/需要X"</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
public class FactPreservingCompressor {

    /** Patterns to extract key facts from conversation */
    private static final List<FactPattern> FACT_PATTERNS = List.of(
            new FactPattern("姓名", Pattern.compile("(?:我叫|我是|我的名字是?|名字叫?)\\s*(\\S{1,10})")),
            new FactPattern("联系方式", Pattern.compile("(?:手机|电话|联系|微信|邮箱)[是为：:]?\\s*([\\d\\w@.+-]{5,30})")),
            new FactPattern("公司", Pattern.compile("(?:我在|我的?公司是?|工作于|就职于)\\s*(\\S{2,20})")),
            new FactPattern("职位", Pattern.compile("(?:我是|我的?职位是?|担任|岗位是?)\\s*(\\S{2,15}(?:工程师|经理|总监|主管|专员|顾问|分析师|设计师|架构师|CTO|CEO|VP|总监|副总))")),
            new FactPattern("偏好", Pattern.compile("(?:我喜欢|我偏好|我希望|我需要|我想要)\\s*(\\S{2,30})"))
    );

    /**
     * Extract key facts from a list of messages.
     *
     * @param messages conversation messages
     * @return list of extracted facts (category: value)
     */
    public Map<String, String> extractFacts(List<Message> messages) {
        Map<String, String> facts = new LinkedHashMap<>();

        for (Message msg : messages) {
            String content = msg.getText();
            if (content == null) continue;

            for (FactPattern pattern : FACT_PATTERNS) {
                var matcher = pattern.pattern().matcher(content);
                if (matcher.find()) {
                    String value = matcher.group(1).trim();
                    if (!value.isBlank() && value.length() >= 2) {
                        facts.putIfAbsent(pattern.category(), value);
                    }
                }
            }
        }

        if (!facts.isEmpty()) {
            log.debug("[FactPreserving] Extracted {} facts from {} messages", facts.size(), messages.size());
        }

        return facts;
    }

    /**
     * Build a fact preservation prefix to prepend to compressed context.
     *
     * @param facts extracted facts
     * @return formatted fact string
     */
    public String buildFactPrefix(Map<String, String> facts) {
        if (facts.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("【已知用户信息】\n");
        for (var entry : facts.entrySet()) {
            sb.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Compress messages while preserving facts.
     *
     * @param messages     original messages
     * @param keepRecent   number of recent messages to keep intact
     * @return compressed result with facts preserved
     */
    public CompressionResult compressWithFactPreservation(List<Message> messages, int keepRecent) {
        // Extract facts from ALL messages (before compression)
        Map<String, String> facts = extractFacts(messages);

        // Split into old (to compress) and recent (to keep)
        int splitIndex = Math.max(0, messages.size() - keepRecent);
        List<Message> oldMessages = messages.subList(0, splitIndex);
        List<Message> recentMessages = messages.subList(splitIndex, messages.size());

        return new CompressionResult(facts, oldMessages, recentMessages);
    }

    /**
     * Fact pattern record.
     */
    private record FactPattern(String category, Pattern pattern) {}

    /**
     * Compression result.
     */
    public record CompressionResult(
            Map<String, String> preservedFacts,
            List<Message> oldMessages,
            List<Message> recentMessages
    ) {
        public String getFactPrefix() {
            return new FactPreservingCompressor().buildFactPrefix(preservedFacts);
        }
    }
}
