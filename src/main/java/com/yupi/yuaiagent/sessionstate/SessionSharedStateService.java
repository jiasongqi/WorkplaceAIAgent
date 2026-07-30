package com.yupi.yuaiagent.sessionstate;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.agent.model.Appointment;
import com.yupi.yuaiagent.repository.AppointmentRepository;
import com.yupi.yuaiagent.validation.InfoValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Shared session scratchpad service — industry-style layered memory:
 * <ul>
 *   <li>Persona (companion / digital employee) stays user-scoped elsewhere</li>
 *   <li>Conversation truth stays in PersistentMessage</li>
 *   <li>This layer holds structured facts + handoff packets readable by any agent</li>
 * </ul>
 */
@Slf4j
@Service
public class SessionSharedStateService {

    private static final int MAX_APPOINTMENT_FACTS = 8;
    private static final int MAX_INJECTION_CHARS = 1800;

    private final SessionSharedStateStore store;
    private final AppointmentRepository appointmentRepository;
    private final InfoValidator infoValidator;
    private final HandoffProtocolService handoffProtocolService;

    public SessionSharedStateService(SessionSharedStateStore store,
                                     AppointmentRepository appointmentRepository,
                                     InfoValidator infoValidator,
                                     HandoffProtocolService handoffProtocolService) {
        this.store = store;
        this.appointmentRepository = appointmentRepository;
        this.infoValidator = infoValidator;
        this.handoffProtocolService = handoffProtocolService;
    }

    public SessionSharedState getOrCreate(String chatId, String userId) {
        return store.findByChatId(chatId).orElseGet(() -> {
            SessionSharedState created = SessionSharedState.builder()
                    .chatId(chatId)
                    .userId(userId)
                    .appointments(new ArrayList<>())
                    .openQuestions(new ArrayList<>())
                    .facts(new LinkedHashMap<>())
                    .agentChain(new ArrayList<>())
                    .updatedAt(LocalDateTime.now())
                    .build();
            return store.save(created);
        });
    }

    /**
     * Record structured handoff when Orchestrator switches specialists.
     * Delegates to {@link HandoffProtocolService} (four-quadrant packet + hop TTL).
     */
    public void recordHandoff(String chatId, String userId, String fromAgent, String toAgent, String note) {
        recordHandoffDetailed(chatId, userId, fromAgent, toAgent, note, note, null);
    }

    /**
     * Full handoff with ACK/NACK result (preferred by Orchestrator).
     */
    public HandoffSanityResult recordHandoffDetailed(
            String chatId,
            String userId,
            String fromAgent,
            String toAgent,
            String userMessage,
            String objectiveHint,
            String traceId) {
        if (handoffProtocolService == null) {
            legacyRecordHandoff(chatId, userId, fromAgent, toAgent, objectiveHint);
            return HandoffSanityResult.ack(null);
        }
        return handoffProtocolService.recordAndValidate(
                chatId, userId, fromAgent, toAgent, userMessage, objectiveHint, traceId);
    }

    private void legacyRecordHandoff(String chatId, String userId, String fromAgent, String toAgent, String note) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(toAgent)) {
            return;
        }
        SessionSharedState state = getOrCreate(chatId, userId);
        state.setUserId(StringUtils.hasText(userId) ? userId : state.getUserId());
        state.setLastAgentType(toAgent);
        StringBuilder handoff = new StringBuilder();
        if (StringUtils.hasText(fromAgent) && !Objects.equals(fromAgent, toAgent)) {
            handoff.append("从 ").append(fromAgent).append(" 切换到 ").append(toAgent);
        } else {
            handoff.append("当前专家：").append(toAgent);
        }
        if (StringUtils.hasText(note)) {
            handoff.append("；").append(note);
        }
        state.setLastHandoffNote(handoff.toString());
        state.setLastHandoffAt(LocalDateTime.now());
        state.setUpdatedAt(LocalDateTime.now());
        store.save(state);
    }

    public void setActiveGoal(String chatId, String userId, String goal) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(goal)) {
            return;
        }
        SessionSharedState state = getOrCreate(chatId, userId);
        state.setActiveGoal(goal.trim());
        state.setUpdatedAt(LocalDateTime.now());
        store.save(state);
    }

    /**
     * Bind perception preprocess output to this session (avoids stuffing long text into SSE URL).
     */
    public void setPerceptionBlock(String chatId, String userId, String promptBlock) {
        if (!StringUtils.hasText(chatId)) {
            return;
        }
        SessionSharedState state = getOrCreate(chatId, userId);
        if (!StringUtils.hasText(promptBlock)) {
            state.setLastPerceptionBlock(null);
        } else {
            String block = promptBlock.trim();
            // Soft cap for prompt budget; keep head (structured fields) more than tail
            if (block.length() > 6000) {
                block = block.substring(0, 6000) + "\n…（感知文本已截断）";
            }
            state.setLastPerceptionBlock(block);
        }
        state.setUpdatedAt(LocalDateTime.now());
        store.save(state);
    }

    /**
     * Infer specialist from bound perception block (docKind / labels).
     * Used so upload+short ask does not fall into NLU ambiguity clarification.
     */
    public AgentIntent suggestIntentFromPerception(String chatId, String userId) {
        if (!StringUtils.hasText(chatId)) {
            return null;
        }
        SessionSharedState state = getOrCreate(chatId, userId);
        String block = state.getLastPerceptionBlock();
        if (!StringUtils.hasText(block)) {
            return null;
        }
        String lower = block.toLowerCase();
        if (lower.contains("dockind=offer") || lower.contains("offersalary")
                || block.contains("Offer") || block.contains("薪资")) {
            return AgentIntent.NEGOTIATION;
        }
        if (lower.contains("dockind=resume") || block.contains("简历")
                || lower.contains("email=") || lower.contains("yearsexperience")) {
            return AgentIntent.RESUME;
        }
        // Default: any perception material → resume specialist
        return AgentIntent.RESUME;
    }

    public void clearPerceptionBlock(String chatId, String userId) {
        setPerceptionBlock(chatId, userId, null);
    }

    public void putFact(String chatId, String userId, String key, String value) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(key)) {
            return;
        }
        SessionSharedState state = getOrCreate(chatId, userId);
        if (!StringUtils.hasText(value)) {
            state.getFacts().remove(key);
        } else {
            state.getFacts().put(key, value.trim());
        }
        state.setUpdatedAt(LocalDateTime.now());
        store.save(state);
    }

    /** After human resume, clear hop TTL so the session is not immediately re-parked. */
    public void resetHandoffHops(String chatId, String userId) {
        if (!StringUtils.hasText(chatId)) {
            return;
        }
        SessionSharedState state = getOrCreate(chatId, userId);
        state.setHopCount(0);
        state.setAgentChain(new ArrayList<>());
        state.setUpdatedAt(LocalDateTime.now());
        store.save(state);
    }

    /**
     * Upsert appointment fact after ConsultationAgent creates a booking.
     */
    public void upsertAppointment(String chatId, String userId, Appointment appointment) {
        if (!StringUtils.hasText(chatId) || appointment == null) {
            return;
        }
        SessionSharedState state = getOrCreate(chatId, userId);
        state.setUserId(StringUtils.hasText(userId) ? userId : state.getUserId());

        SessionSharedState.AppointmentFact fact = toFact(appointment);
        List<SessionSharedState.AppointmentFact> list = state.getAppointments();
        if (list == null) {
            list = new ArrayList<>();
            state.setAppointments(list);
        }
        list.removeIf(a -> Objects.equals(a.getAppointmentId(), fact.getAppointmentId()));
        list.add(0, fact);
        while (list.size() > MAX_APPOINTMENT_FACTS) {
            list.remove(list.size() - 1);
        }

        if (StringUtils.hasText(appointment.getName())) {
            state.getFacts().put("name", appointment.getName());
        }
        if (StringUtils.hasText(appointment.getContact())) {
            state.getFacts().put("contact", appointment.getContact());
        }
        if (StringUtils.hasText(appointment.getTopic())) {
            state.getFacts().put("lastConsultationTopic", appointment.getTopic());
        }
        state.setActiveGoal("已有预约，可查询/修改日程");
        state.setUpdatedAt(LocalDateTime.now());
        store.save(state);
        log.info("[SharedState] upsert appointment {} for chatId={}", fact.getAppointmentId(), chatId);
    }

    /**
     * Prompt injection block for any specialist in this session.
     * Facts and packet mission are listed before narrative notes (anti telephone-game).
     */
    public String buildPromptInjection(String chatId, String userId) {
        if (!StringUtils.hasText(chatId)) {
            return "";
        }
        SessionSharedState state = getOrCreate(chatId, userId);
        hydrateAppointmentsIfEmpty(state);

        StringBuilder sb = new StringBuilder();
        sb.append("【会话共享状态 Shared Session State】\n");
        sb.append("优先级：结构化事实与交付物引用 > 交接任务说明 > 近期对话摘要。摘要与事实冲突时以事实为准。\n");

        // Perception first — must not be truncated away by the soft char budget
        boolean hasPerception = StringUtils.hasText(state.getLastPerceptionBlock());
        if (hasPerception) {
            sb.append('\n').append(state.getLastPerceptionBlock()).append('\n');
        }

        // ── Immutable facts first (Context.keyFacts / appointments) ──
        if (state.getFacts() != null && !state.getFacts().isEmpty()) {
            sb.append("- 已知事实（不可被摘要覆盖）：");
            state.getFacts().forEach((k, v) -> sb.append(k).append('=').append(v).append("; "));
            sb.append('\n');
        }
        if (state.getAppointments() != null && !state.getAppointments().isEmpty()) {
            sb.append("- 预约日程（引用 ID，勿编造）：\n");
            for (SessionSharedState.AppointmentFact a : state.getAppointments()) {
                sb.append("  · ")
                        .append(a.getTopic() != null ? a.getTopic() : "咨询")
                        .append(" | ")
                        .append(a.getAppointmentTime() != null ? a.getAppointmentTime() : "时间待定")
                        .append(" | ")
                        .append(a.getName() != null ? a.getName() : "—")
                        .append(" | 编号 ")
                        .append(a.getAppointmentId())
                        .append(" | ")
                        .append(a.getStatus() != null ? a.getStatus() : "")
                        .append('\n');
            }
            // Evidence penetration — raw fields so downstream agents don't rely on paraphrases only
            sb.append("- 证据穿透（原始字段，优先于转述）：\n");
            for (SessionSharedState.AppointmentFact a : state.getAppointments()) {
                sb.append("  · raw{appointmentId=")
                        .append(nullSafe(a.getAppointmentId()))
                        .append("; name=").append(nullSafe(a.getName()))
                        .append("; contact=").append(nullSafe(a.getContact()))
                        .append("; topic=").append(nullSafe(a.getTopic()))
                        .append("; time=").append(nullSafe(a.getAppointmentTime()))
                        .append("; status=").append(nullSafe(a.getStatus()))
                        .append("}\n");
            }
        } else {
            sb.append("- 预约日程：本会话暂无\n");
        }

        if (StringUtils.hasText(state.getActiveGoal())) {
            sb.append("- 当前目标：").append(state.getActiveGoal()).append('\n');
        }

        // ── Structured packet (Meta / Mission / Artifacts) ──
        HandoffPacket packet = state.getLastHandoffPacket();
        if (packet != null) {
            appendPacket(sb, packet);
        } else {
            if (StringUtils.hasText(state.getLastAgentType())) {
                sb.append("- 上一任专家：").append(state.getLastAgentType()).append('\n');
            }
            if (StringUtils.hasText(state.getLastHandoffNote())) {
                sb.append("- 交接说明：").append(state.getLastHandoffNote()).append('\n');
            }
        }

        String text = sb.toString();
        int maxChars = hasPerception ? 5000 : MAX_INJECTION_CHARS;
        if (text.length() > maxChars) {
            return text.substring(0, maxChars) + "…";
        }
        return text;
    }

    private void appendPacket(StringBuilder sb, HandoffPacket packet) {
        sb.append("- Handoff Packet：\n");
        if (packet.getMeta() != null) {
            sb.append("  · Meta：")
                    .append(packet.getMeta().getSourceAgent()).append(" → ")
                    .append(packet.getMeta().getTargetAgent())
                    .append(" | hop=").append(packet.getMeta().getHopCount())
                    .append(" | id=").append(packet.getMeta().getHandoffId())
                    .append('\n');
        }
        if (packet.getMission() != null) {
            if (StringUtils.hasText(packet.getMission().getObjective())) {
                sb.append("  · Mission.objective：").append(packet.getMission().getObjective()).append('\n');
            }
            if (StringUtils.hasText(packet.getMission().getDefinitionOfDone())) {
                sb.append("  · Mission.DOD：").append(packet.getMission().getDefinitionOfDone()).append('\n');
            }
            if (packet.getMission().getConstraints() != null && !packet.getMission().getConstraints().isEmpty()) {
                sb.append("  · Mission.constraints：")
                        .append(String.join("；", packet.getMission().getConstraints()))
                        .append('\n');
            }
        }
        if (packet.getArtifacts() != null) {
            if (packet.getArtifacts().getAppointmentIds() != null
                    && !packet.getArtifacts().getAppointmentIds().isEmpty()) {
                sb.append("  · Artifacts.appointments：")
                        .append(String.join(", ", packet.getArtifacts().getAppointmentIds()))
                        .append('\n');
            }
            if (packet.getArtifacts().getArtifactIds() != null
                    && !packet.getArtifacts().getArtifactIds().isEmpty()) {
                sb.append("  · Artifacts.ids：")
                        .append(String.join(", ", packet.getArtifacts().getArtifactIds()))
                        .append(packet.getArtifacts().isValidated() ? " (已校验)" : " (未校验)")
                        .append('\n');
            }
        }
        if (packet.getScope() != null && !packet.getScope().isEmpty()) {
            sb.append("  · Scope（本轮工具上限）：").append(String.join(", ", packet.getScope())).append('\n');
        }
    }

    private void hydrateAppointmentsIfEmpty(SessionSharedState state) {
        if (state.getAppointments() != null && !state.getAppointments().isEmpty()) {
            return;
        }
        if (appointmentRepository == null || !StringUtils.hasText(state.getChatId())) {
            return;
        }
        try {
            List<Appointment> fromRepo = appointmentRepository.findByChatId(state.getChatId());
            if (fromRepo == null || fromRepo.isEmpty()) {
                return;
            }
            List<SessionSharedState.AppointmentFact> facts = new ArrayList<>();
            for (Appointment a : fromRepo) {
                if (facts.size() >= MAX_APPOINTMENT_FACTS) {
                    break;
                }
                facts.add(toFact(a));
            }
            state.setAppointments(facts);
            state.setUpdatedAt(LocalDateTime.now());
            store.save(state);
        } catch (Exception e) {
            log.debug("[SharedState] hydrate appointments skipped: {}", e.getMessage());
        }
    }

    private SessionSharedState.AppointmentFact toFact(Appointment appointment) {
        String time = null;
        if (appointment.getAppointmentTime() != null && infoValidator != null) {
            time = infoValidator.formatDateTime(appointment.getAppointmentTime());
        } else if (appointment.getAppointmentTime() != null) {
            time = appointment.getAppointmentTime().toString();
        }
        String status = appointment.getStatus() != null
                ? appointment.getStatus().getDescription()
                : null;
        return SessionSharedState.AppointmentFact.builder()
                .appointmentId(appointment.getAppointmentId())
                .name(appointment.getName())
                .contact(appointment.getContact())
                .topic(appointment.getTopic())
                .appointmentTime(time)
                .status(status)
                .recordedAt(LocalDateTime.now())
                .build();
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
