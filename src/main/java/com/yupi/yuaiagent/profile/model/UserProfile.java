package com.yupi.yuaiagent.profile.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户画像实体
 * 按 userId 唯一标识，每个 userId 至多对应一份画像。
 * 包含沟通偏好、语气偏好、关注领域、已知背景、历史诉求等维度。
 * 约束：updatedAt 应大于或等于 createdAt（由后续逻辑保证）。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    /**
     * 用户唯一标识
     */
    private String userId;

    /**
     * 沟通偏好（简洁 / 详细）
     */
    private CommunicationPreference communicationPreference;

    /**
     * 语气偏好（如 鼓励型 / 直接型）
     */
    private String tonePreference;

    /**
     * 关注领域（列表，去重累积）
     */
    @Builder.Default
    private List<String> focusAreas = new ArrayList<>();

    /**
     * 已知背景（如 行业、岗位、年限）
     */
    private String knownBackground;

    /**
     * 历史诉求（列表，去重累积）
     */
    @Builder.Default
    private List<String> historicalDemands = new ArrayList<>();

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间（始终 >= createdAt）
     */
    private LocalDateTime updatedAt;
}
