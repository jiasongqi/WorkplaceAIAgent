package com.yupi.yuaiagent.artifact;

import com.yupi.yuaiagent.artifact.model.ArtifactLifecycleEvent;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 交付物生命周期管理 — 控制状态流转的合法性并记录审计事件。
 * <p>
 * 合法流转：
 * <ul>
 *     <li>DRAFT → REVIEWING（提交审核）</li>
 *     <li>REVIEWING → APPROVED（审核通过）</li>
 *     <li>REVIEWING → DRAFT（审核拒绝，含原因）</li>
 *     <li>APPROVED → PUBLISHED（发布）</li>
 *     <li>任意 → ARCHIVED（归档）</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Component
public class ArtifactLifecycleManager {

    @Resource
    private ArtifactRepository artifactRepository;

    private final List<ArtifactLifecycleEvent> eventHistory = Collections.synchronizedList(new ArrayList<>());

    /**
     * 提交审核：DRAFT → REVIEWING
     */
    public boolean submitForReview(String artifactId, String operator) {
        return transition(artifactId, ArtifactStatus.DRAFT, ArtifactStatus.REVIEWING, operator, null);
    }

    /**
     * 审核通过：REVIEWING → APPROVED
     */
    public boolean approve(String artifactId, String operator) {
        return transition(artifactId, ArtifactStatus.REVIEWING, ArtifactStatus.APPROVED, operator, null);
    }

    /**
     * 审核拒绝：REVIEWING → DRAFT
     */
    public boolean reject(String artifactId, String operator, String reason) {
        return transition(artifactId, ArtifactStatus.REVIEWING, ArtifactStatus.DRAFT, operator, reason);
    }

    /**
     * 发布：APPROVED → PUBLISHED
     */
    public boolean publish(String artifactId, String operator) {
        return transition(artifactId, ArtifactStatus.APPROVED, ArtifactStatus.PUBLISHED, operator, null);
    }

    /**
     * 归档：任意 → ARCHIVED
     */
    public boolean archive(String artifactId, String operator) {
        var artifact = artifactRepository.findById(artifactId);
        if (artifact.isEmpty()) {
            return false;
        }
        ArtifactStatus fromStatus = artifact.get().getStatus();
        artifactRepository.updateStatus(artifactId, ArtifactStatus.ARCHIVED);
        recordEvent(artifactId, fromStatus, ArtifactStatus.ARCHIVED, operator, "归档");
        log.info("[Lifecycle] 交付物已归档: artifactId={}, from={}", artifactId, fromStatus);
        return true;
    }

    /**
     * 执行状态流转（含合法性校验）
     */
    private boolean transition(String artifactId, ArtifactStatus expectedFrom,
                                ArtifactStatus to, String operator, String reason) {
        var artifact = artifactRepository.findById(artifactId);
        if (artifact.isEmpty()) {
            log.warn("[Lifecycle] 交付物不存在: {}", artifactId);
            return false;
        }

        ArtifactStatus currentStatus = artifact.get().getStatus();
        // 兼容旧状态：PENDING 视为 DRAFT，READY 视为 APPROVED
        if (currentStatus == ArtifactStatus.PENDING && expectedFrom == ArtifactStatus.DRAFT) {
            // 允许
        } else if (currentStatus == ArtifactStatus.READY && expectedFrom == ArtifactStatus.APPROVED) {
            // 允许
        } else if (currentStatus != expectedFrom) {
            log.warn("[Lifecycle] 非法流转: artifactId={}, current={}, expected={}, target={}",
                    artifactId, currentStatus, expectedFrom, to);
            return false;
        }

        artifactRepository.updateStatus(artifactId, to);
        recordEvent(artifactId, currentStatus, to, operator, reason);
        log.info("[Lifecycle] 状态流转: artifactId={}, {} -> {}", artifactId, currentStatus, to);
        return true;
    }

    private void recordEvent(String artifactId, ArtifactStatus from, ArtifactStatus to,
                              String operator, String reason) {
        ArtifactLifecycleEvent event = ArtifactLifecycleEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .artifactId(artifactId)
                .fromStatus(from)
                .toStatus(to)
                .operator(operator)
                .timestamp(LocalDateTime.now())
                .reason(reason)
                .build();
        eventHistory.add(event);
    }

    /**
     * 获取交付物的生命周期事件历史
     */
    public List<ArtifactLifecycleEvent> getHistory(String artifactId) {
        return eventHistory.stream()
                .filter(e -> artifactId.equals(e.getArtifactId()))
                .toList();
    }
}
