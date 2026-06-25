package com.yupi.yuaiagent.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * Event bus adapter — wraps Spring ApplicationEventPublisher with async publish.
 *
 * <p>All governance events are published asynchronously so that listener execution
 * (audit logging, metrics, notifications) never blocks the main Agent/Workflow flow.</p>
 *
 * @author jsq
 */
@Slf4j
@Component
public class EventBusAdapter {

    @Resource
    private ApplicationEventPublisher eventPublisher;

    /**
     * Publish governance event asynchronously.
     * Listeners run in a separate thread — main flow is never blocked.
     */
    @Async
    public void publish(GovernanceEvent event) {
        log.debug("[EventBus] publishing async: type={}, eventId={}", event.getEventType(), event.getEventId());
        eventPublisher.publishEvent(event);
    }

    /**
     * 发布权限拒绝事件
     */
    public void publishAccessDenied(String userId, String agentCode, String toolName, String reason) {
        publish(new AccessDeniedEvent(this, userId, agentCode, toolName, reason));
    }

    /**
     * 发布沙箱执行事件
     */
    public void publishSandboxExec(String userId, String agentCode,
                                    String command, boolean success, long durationMs) {
        publish(new SandboxExecEvent(this, userId, agentCode, command, success, durationMs));
    }
}
