package com.yupi.yuaiagent.artifact.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交付物生命周期事件 — 记录状态变更的完整审计信息。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactLifecycleEvent {

    /** 事件 ID */
    private String eventId;

    /** 交付物 ID */
    private String artifactId;

    /** 变更前状态 */
    private ArtifactStatus fromStatus;

    /** 变更后状态 */
    private ArtifactStatus toStatus;

    /** 操作人 */
    private String operator;

    /** 操作时间 */
    private LocalDateTime timestamp;

    /** 变更原因（如拒绝原因） */
    private String reason;
}
