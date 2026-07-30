package com.yupi.yuaiagent.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 治理事件日志监听器 — 统一记录治理层事件日志。
 * <p>
 * 后续可扩展为：发送告警、写入审计数据库、推送消息队列。
 *
 * @author jsq
 */
@Slf4j
@Component
public class GovernanceEventListener {

    @EventListener
    public void onAccessDenied(AccessDeniedEvent event) {
        log.warn("[Governance] ACCESS_DENIED: userId={}, agent={}, tool={}, reason={}, eventId={}",
                event.getUserId(), event.getAgentCode(), event.getToolName(),
                event.getReason(), event.getEventId());
    }

    @EventListener
    public void onSandboxExec(SandboxExecEvent event) {
        log.info("[Governance] SANDBOX_EXEC: userId={}, agent={}, success={}, duration={}ms, eventId={}",
                event.getUserId(), event.getAgentCode(),
                event.isSuccess(), event.getDurationMs(), event.getEventId());
        try {
            com.yupi.yuaiagent.controller.LastSandboxExecHolder.put(
                    event.getUserId(), event.getCommand(), event.isSuccess(), event.getDurationMs());
            var ctx = com.yupi.yuaiagent.trace.TraceContextHolder.get();
            if (ctx != null) {
                var span = ctx.appendSpan(
                        com.yupi.yuaiagent.trace.model.TraceStepType.TOOL_CALL,
                        "沙箱执行: " + (event.getCommand() != null ? event.getCommand() : ""));
                if (span != null) {
                    span.putMetadata("sandboxSuccess", String.valueOf(event.isSuccess()));
                    span.putMetadata("sandboxDurationMs", String.valueOf(event.getDurationMs()));
                    if (event.isSuccess()) {
                        ctx.finishSpan(span);
                    } else {
                        ctx.failSpan(span, "sandbox exec failed");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Governance] sandbox→trace attach skipped: {}", e.getMessage());
        }
    }
}
