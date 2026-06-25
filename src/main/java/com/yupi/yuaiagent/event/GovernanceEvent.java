package com.yupi.yuaiagent.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 治理层领域事件基类 — 统一事件格式，便于后续对接 RocketMQ/Kafka。
 *
 * @author jsq
 */
@Getter
public abstract class GovernanceEvent extends ApplicationEvent {

    private final String eventId;
    private final String eventType;
    private final String userId;

    public GovernanceEvent(Object source, String eventType, String userId) {
        super(source);
        this.eventId = java.util.UUID.randomUUID().toString();
        this.eventType = eventType;
        this.userId = userId;
    }
}
