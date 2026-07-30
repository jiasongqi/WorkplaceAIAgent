package com.yupi.yuaiagent.hitl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional remote notification for pending HITL approvals (Feishu/DingTalk webhook).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HitlNotifyService {

    private final HitlProperties properties;

    public void notifyPending(HumanApprovalService.ApprovalRequest req) {
        if (req == null) {
            return;
        }
        String text = """
                【WorkPilot 待确认】%s
                摘要：%s
                approvalId：%s
                请在对话中确认，或调用 POST /hitl/approve
                """.formatted(req.getActionType(), req.getSummary(), req.getApprovalId());
        postWebhook(text, req.getApprovalId());
    }

    public void notifyHumanHandoff(HumanHandoffTicket ticket) {
        if (ticket == null) {
            return;
        }
        String text = """
                【WorkPilot 人工接管】原因：%s
                摘要：%s
                handoffId：%s
                chatId：%s
                请回复对话续跑，或 POST /hitl/handoff/resume
                """.formatted(
                ticket.getParkReason(),
                ticket.getParkSummary(),
                ticket.getHandoffId(),
                ticket.getChatId());
        postWebhook(text, ticket.getHandoffId());
    }

    private void postWebhook(String text, String id) {
        String webhook = properties.getNotifyWebhook();
        if (!StringUtils.hasText(webhook)) {
            log.debug("[HITL] no notify webhook configured, skip remote notify for {}", id);
            return;
        }
        try {
            RestTemplate rest = new RestTemplateBuilder()
                    .setConnectTimeout(Duration.ofSeconds(5))
                    .setReadTimeout(Duration.ofSeconds(5))
                    .build();

            Map<String, Object> body = new HashMap<>();
            body.put("msg_type", "text");
            body.put("content", Map.of("text", text));
            body.put("msgtype", "text");
            body.put("text", Map.of("content", text));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.postForEntity(webhook, new HttpEntity<>(body, headers), String.class);
            log.info("[HITL] remote notify sent for {}", id);
        } catch (Exception e) {
            log.warn("[HITL] remote notify failed: {}", e.getMessage());
        }
    }
}
