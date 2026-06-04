package com.yupi.yuaiagent.profile;

import com.yupi.yuaiagent.profile.model.CommunicationPreference;
import com.yupi.yuaiagent.profile.model.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 用户画像提示词构建器。
 * 将 {@link UserProfile} 转换为可拼接到各 Agent system prompt 的中文提示片段，
 * 用于实现画像驱动的个性化回答。
 *
 * <p>构建规则：
 * <ul>
 *     <li>沟通偏好：CONCISE → 指示"请以简洁方式回答"；DETAILED → 指示"请以详细方式回答"</li>
 *     <li>语气偏好：非空时纳入，定制回答风格</li>
 *     <li>已知背景：非空时纳入，以减少重复追问</li>
 *     <li>关注领域：非空时纳入</li>
 * </ul>
 *
 * <p>最终片段长度受配置 {@code profile.injection.max-chars}（默认 1000）约束，超出则截断；
 * 当 profile 为 null 或各维度均为空时返回空字符串。
 *
 * @author jsq
 */
@Component
public class ProfilePromptBuilder {

    /**
     * 画像注入到 system prompt 的字符上限（默认 1000）。
     */
    @Value("${profile.injection.max-chars:1000}")
    private int maxChars;

    /**
     * 将用户画像转换为可拼接到 system prompt 的中文提示片段。
     *
     * @param profile 用户画像，可能为 null
     * @return 提示片段；当 profile 为 null 或各维度均为空时返回空字符串 ""，
     * 拼接结果超过 maxChars 时截断到上限
     */
    public String build(UserProfile profile) {
        if (profile == null) {
            return "";
        }

        StringBuilder body = new StringBuilder();

        // 沟通偏好（Req 12.2 / 12.3）
        CommunicationPreference preference = profile.getCommunicationPreference();
        if (preference == CommunicationPreference.CONCISE) {
            body.append("请以简洁方式回答。");
        } else if (preference == CommunicationPreference.DETAILED) {
            body.append("请以详细方式回答。");
        }

        // 语气偏好（Req 19.1）
        if (StringUtils.hasText(profile.getTonePreference())) {
            body.append("语气偏好：").append(profile.getTonePreference()).append("。");
        }

        // 已知背景（Req 19.2）：纳入以减少重复追问
        if (StringUtils.hasText(profile.getKnownBackground())) {
            body.append("已知背景：").append(profile.getKnownBackground()).append("。");
        }

        // 关注领域（Req 19.1 / 19.2 个性化）
        if (!CollectionUtils.isEmpty(profile.getFocusAreas())) {
            body.append("关注领域：").append(String.join("、", profile.getFocusAreas())).append("。");
        }

        // 各维度均为空时返回空字符串
        if (body.length() == 0) {
            return "";
        }

        String result = "【用户画像】" + body;

        // 字符上限约束（Req 19.3）：超出则截断到上限
        if (maxChars > 0 && result.length() > maxChars) {
            return result.substring(0, maxChars);
        }
        return result;
    }
}
