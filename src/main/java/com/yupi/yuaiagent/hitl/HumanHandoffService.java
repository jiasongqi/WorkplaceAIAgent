package com.yupi.yuaiagent.hitl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.sessionstate.HandoffPacket;
import com.yupi.yuaiagent.sessionstate.SessionSharedStateService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Async human handoff: serialize Packet → WAITING_FOR_HUMAN → release request →
 * human resume (API / next turn) hydrates SharedState and continues.
 * <p>
 * Does not sleep on SSE threads — same event-driven pattern as tool-level HITL.
 */
@Slf4j
@Service
public class HumanHandoffService {

    private final HitlProperties properties;
    private final HitlNotifyService notifyService;
    private final SessionSharedStateService sessionSharedStateService;
    private final Map<String, HumanHandoffTicket> store = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Value("${app.hitl.storage-dir:./tmp/hitl}")
    private String storageDir;

    private File storageFile;

    public HumanHandoffService(HitlProperties properties,
                               HitlNotifyService notifyService,
                               SessionSharedStateService sessionSharedStateService) {
        this.properties = properties;
        this.notifyService = notifyService;
        this.sessionSharedStateService = sessionSharedStateService;
    }

    @PostConstruct
    public void init() {
        try {
            File dir = new File(storageDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            storageFile = new File(dir, "human-handoffs.json");
            if (storageFile.exists()) {
                List<HumanHandoffTicket> loaded = objectMapper.readValue(storageFile, new TypeReference<>() {});
                if (loaded != null) {
                    for (HumanHandoffTicket t : loaded) {
                        if (t.getHandoffId() != null) {
                            store.put(t.getHandoffId(), refreshExpiry(t));
                        }
                    }
                }
            }
            log.info("[HumanHandoff] loaded {} tickets from {}", store.size(), storageFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("[HumanHandoff] load failed: {}", e.getMessage());
        }
    }

    /**
     * Park current work for a human operator; returns ticket (already persisted).
     */
    public HumanHandoffTicket park(String chatId, String userId, HandoffPacket packet,
                                   String parkReason, String parkSummary) {
        Instant now = Instant.now();
        int ttl = Math.max(60, properties.getHumanHandoffTtlSeconds());
        String id = packet != null && packet.getMeta() != null
                && StringUtils.hasText(packet.getMeta().getHandoffId())
                ? packet.getMeta().getHandoffId()
                : "hh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // One waiting ticket per chat — cancel previous
        findWaitingByChatId(chatId).ifPresent(prev -> {
            prev.setStatus(HumanHandoffTicket.Status.CANCELLED);
            store.put(prev.getHandoffId(), prev);
        });

        HumanHandoffTicket ticket = HumanHandoffTicket.builder()
                .handoffId(id)
                .chatId(chatId)
                .userId(StringUtils.hasText(userId) ? userId : "anonymous")
                .status(HumanHandoffTicket.Status.WAITING_FOR_HUMAN)
                .parkReason(parkReason)
                .parkSummary(parkSummary)
                .packet(packet)
                .createdAt(now)
                .expiresAt(now.plusSeconds(ttl))
                .build();
        store.put(id, ticket);
        persist();

        if (sessionSharedStateService != null && StringUtils.hasText(chatId)) {
            sessionSharedStateService.putFact(chatId, userId, "waitingHumanHandoffId", id);
            sessionSharedStateService.setActiveGoal(chatId, userId, "等待人工接管: " + (parkReason != null ? parkReason : ""));
        }

        try {
            notifyService.notifyHumanHandoff(ticket);
        } catch (Exception e) {
            log.debug("[HumanHandoff] notify skipped: {}", e.getMessage());
        }
        log.info("[HumanHandoff] parked id={} chatId={} reason={}", id, chatId, parkReason);
        return ticket;
    }

    public Optional<HumanHandoffTicket> get(String handoffId) {
        return Optional.ofNullable(store.get(handoffId)).map(this::refreshExpiry);
    }

    public Optional<HumanHandoffTicket> findWaitingByChatId(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return Optional.empty();
        }
        return store.values().stream()
                .map(this::refreshExpiry)
                .filter(t -> t != null
                        && t.getStatus() == HumanHandoffTicket.Status.WAITING_FOR_HUMAN
                        && chatId.equals(t.getChatId()))
                .findFirst();
    }

    public List<HumanHandoffTicket> listWaitingByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        List<HumanHandoffTicket> list = new ArrayList<>();
        for (HumanHandoffTicket t : store.values()) {
            HumanHandoffTicket r = refreshExpiry(t);
            if (r != null
                    && r.getStatus() == HumanHandoffTicket.Status.WAITING_FOR_HUMAN
                    && userId.equals(r.getUserId())) {
                list.add(r);
            }
        }
        return list;
    }

    /**
     * Human resumes: inject input into SharedState and mark RESUMED (stateless wake).
     */
    public HumanHandoffTicket resume(String handoffId, String userId, String humanInput) {
        HumanHandoffTicket ticket = requireOwned(handoffId, userId);
        if (ticket.getStatus() == HumanHandoffTicket.Status.EXPIRED) {
            throw new IllegalStateException("human handoff expired");
        }
        if (ticket.getStatus() == HumanHandoffTicket.Status.CANCELLED) {
            throw new IllegalStateException("human handoff cancelled");
        }
        if (ticket.getStatus() == HumanHandoffTicket.Status.RESUMED) {
            return ticket; // idempotent
        }
        if (ticket.getStatus() != HumanHandoffTicket.Status.WAITING_FOR_HUMAN) {
            throw new IllegalStateException("human handoff not waiting: " + ticket.getStatus());
        }

        ticket.setHumanInput(humanInput != null ? humanInput.trim() : "");
        ticket.setStatus(HumanHandoffTicket.Status.RESUMED);
        ticket.setResumedAt(Instant.now());
        store.put(handoffId, ticket);
        persist();

        if (sessionSharedStateService != null && StringUtils.hasText(ticket.getChatId())) {
            sessionSharedStateService.putFact(ticket.getChatId(), ticket.getUserId(),
                    "humanHandoffInput", ticket.getHumanInput());
            sessionSharedStateService.putFact(ticket.getChatId(), ticket.getUserId(),
                    "humanHandoffResumedId", handoffId);
            sessionSharedStateService.putFact(ticket.getChatId(), ticket.getUserId(),
                    "waitingHumanHandoffId", "");
            sessionSharedStateService.setActiveGoal(ticket.getChatId(), ticket.getUserId(),
                    "人工已接管并恢复，继续完成本轮目标");
            // Reset hop TTL so resume is not immediately re-parked
            sessionSharedStateService.resetHandoffHops(ticket.getChatId(), ticket.getUserId());
            sessionSharedStateService.recordHandoff(
                    ticket.getChatId(), ticket.getUserId(),
                    "HUMAN", "GENERAL",
                    "人工续跑：" + truncate(ticket.getHumanInput(), 80));
        }
        log.info("[HumanHandoff] resumed id={} chatId={}", handoffId, ticket.getChatId());
        return ticket;
    }

    public HumanHandoffTicket cancel(String handoffId, String userId) {
        HumanHandoffTicket ticket = requireOwned(handoffId, userId);
        ticket.setStatus(HumanHandoffTicket.Status.CANCELLED);
        store.put(handoffId, ticket);
        persist();
        if (sessionSharedStateService != null && StringUtils.hasText(ticket.getChatId())) {
            sessionSharedStateService.putFact(ticket.getChatId(), ticket.getUserId(),
                    "waitingHumanHandoffId", "");
        }
        return ticket;
    }

    public String pendingMessage(HumanHandoffTicket ticket) {
        return """
                ### 需要人工接管

                机器侧已暂停本轮自动换人（原因：%s）。

                %s

                会话状态已持久化，**不会占用服务线程等待**。您可以：
                - 直接在本对话回复补充说明（下一轮将自动续跑）
                - 或调用 `POST /hitl/handoff/resume?handoffId=%s`

                <!--human-handoff:%s-->
                """.formatted(
                ticket.getParkReason() != null ? ticket.getParkReason() : "escalation",
                ticket.getParkSummary() != null ? ticket.getParkSummary() : "",
                ticket.getHandoffId(),
                ticket.getHandoffId());
    }

    private HumanHandoffTicket requireOwned(String handoffId, String userId) {
        HumanHandoffTicket ticket = refreshExpiry(store.get(handoffId));
        if (ticket == null) {
            throw new IllegalArgumentException("human handoff not found");
        }
        if (userId != null && ticket.getUserId() != null
                && !"anonymous".equals(ticket.getUserId())
                && !ticket.getUserId().equals(userId)) {
            throw new IllegalArgumentException("human handoff belongs to another user");
        }
        return ticket;
    }

    private HumanHandoffTicket refreshExpiry(HumanHandoffTicket ticket) {
        if (ticket == null) {
            return null;
        }
        if (ticket.getStatus() == HumanHandoffTicket.Status.WAITING_FOR_HUMAN
                && ticket.getExpiresAt() != null
                && Instant.now().isAfter(ticket.getExpiresAt())) {
            ticket.setStatus(HumanHandoffTicket.Status.EXPIRED);
            persist();
        }
        return ticket;
    }

    private synchronized void persist() {
        if (storageFile == null) {
            return;
        }
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storageFile, new ArrayList<>(store.values()));
        } catch (Exception e) {
            log.warn("[HumanHandoff] persist failed: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
