package com.yupi.yuaiagent.artifact.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交付物列表展示用的轻量摘要。
 * <p>
 * 仅包含 artifactId、type、producer、title、status、createdAt 字段，
 * 用于管理员交付物列表接口（Req 17.2），避免列表展示时泄露完整 {@code content}。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactSummary {

    /**
     * 全局唯一 ID
     */
    private String artifactId;

    /**
     * 交付物类型，如 DATA_ANALYSIS_REPORT
     */
    private String type;

    /**
     * 生产者标识名（数据员工名称）
     */
    private String producer;

    /**
     * 标题
     */
    private String title;

    /**
     * 状态
     */
    private ArtifactStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 被智能召回推荐的次数。
     */
    private long offeredCount;

    /**
     * 被下游 Agent 明确采用的次数。
     */
    private long adoptedCount;

    /**
     * 由完整 {@link Artifact} 构建轻量摘要（仅保留展示字段）。
     */
    public static ArtifactSummary from(Artifact artifact) {
        return from(artifact, 0, 0);
    }

    public static ArtifactSummary from(Artifact artifact, long offeredCount, long adoptedCount) {
        return ArtifactSummary.builder()
                .artifactId(artifact.getArtifactId())
                .type(artifact.getType())
                .producer(artifact.getProducer())
                .title(artifact.getTitle())
                .status(artifact.getStatus())
                .createdAt(artifact.getCreatedAt())
                .offeredCount(offeredCount)
                .adoptedCount(adoptedCount)
                .build();
    }
}
