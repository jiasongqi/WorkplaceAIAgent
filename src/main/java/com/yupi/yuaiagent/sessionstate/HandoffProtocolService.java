package com.yupi.yuaiagent.sessionstate;

import com.yupi.yuaiagent.artifact.ArtifactShelf;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handoff protocol: build four-quadrant packets, hop/TTL guards, ACK/NACK sanity,
 * artifact existence checks, and permission-scope install.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffProtocolService {

    private final SessionSharedStateStore store;
    private final ArtifactShelf artifactShelf;

    /**
     * Default minimum tool scope per target intent (permission downgrade on handoff).
     */
    public List<String> defaultScopeFor(String targetAgent) {
        if (!StringUtils.hasText(targetAgent)) {
            return List.of("rag.query");
        }
        return switch (targetAgent.toUpperCase()) {
            case "RESUME" -> List.of("resume.*", "rag.query");
            case "NEGOTIATION" -> List.of("negotiation.*", "rag.query");
            case "ESCAPE" -> List.of("escape.*", "rag.query");
            case "CONSULTATION" -> List.of("consultation.*", "calendar.*", "rag.query");
            case "DATA_QUERY" -> List.of("data.*", "rag.query");
            case "DIGITAL_EMPLOYEE" -> List.of("digital_employee.*", "rag.query");
            default -> List.of("general.*", "rag.query");
        };
    }

    /**
     * Build + persist a structured handoff packet; returns NACK if hop TTL exceeded.
     */
    public HandoffSanityResult recordAndValidate(
            String chatId,
            String userId,
            String fromAgent,
            String toAgent,
            String userMessage,
            String objectiveHint,
            String traceId) {

        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(toAgent)) {
            return HandoffSanityResult.nack("invalid_handoff", "chatId/toAgent required", null);
        }

        SessionSharedState state = store.findByChatId(chatId).orElseGet(() ->
                SessionSharedState.builder()
                        .chatId(chatId)
                        .userId(userId)
                        .appointments(new ArrayList<>())
                        .openQuestions(new ArrayList<>())
                        .facts(new LinkedHashMap<>())
                        .agentChain(new ArrayList<>())
                        .updatedAt(LocalDateTime.now())
                        .build());

        boolean switched = StringUtils.hasText(fromAgent) && !Objects.equals(fromAgent, toAgent);
        int hop = state.getHopCount() <= 0 ? 0 : state.getHopCount();
        if (switched) {
            hop += 1;
        } else if (hop == 0) {
            hop = 1;
        }

        List<String> chain = state.getAgentChain() != null
                ? new ArrayList<>(state.getAgentChain()) : new ArrayList<>();
        if (StringUtils.hasText(fromAgent) && (chain.isEmpty() || !Objects.equals(chain.get(chain.size() - 1), fromAgent))) {
            chain.add(fromAgent);
        }
        if (chain.isEmpty() || !Objects.equals(chain.get(chain.size() - 1), toAgent)) {
            chain.add(toAgent);
        }
        while (chain.size() > 12) {
            chain.remove(0);
        }

        if (hop > HandoffPacket.DEFAULT_MAX_HOPS) {
            HandoffPacket blocked = buildPacket(state, fromAgent, toAgent, userMessage,
                    objectiveHint, traceId, hop, chain, false);
            log.warn("[Handoff] TTL exceeded chatId={} hop={} {} -> {}", chatId, hop, fromAgent, toAgent);
            return HandoffSanityResult.nack(
                    "hop_ttl_exceeded",
                    "移交跳数超过 " + HandoffPacket.DEFAULT_MAX_HOPS + "，请停止换人并直接回答或转人工",
                    blocked);
        }

        // Ping-pong only when thrashing: high hop + A-B-A (star topology often revisits agents normally)
        if (switched && hop >= 4 && chain.size() >= 3) {
            String a = chain.get(chain.size() - 3);
            String c = chain.get(chain.size() - 1);
            if (Objects.equals(a, c)) {
                log.warn("[Handoff] ping-pong detected chain={} hop={}", chain, hop);
                HandoffPacket blocked = buildPacket(state, fromAgent, toAgent, userMessage,
                        objectiveHint, traceId, hop, chain, false);
                return HandoffSanityResult.nack(
                        "ping_pong_loop",
                        "检测到专家来回踢皮球，请当前顾问直接处理或向用户澄清，勿再次移交",
                        blocked);
            }
        }

        HandoffPacket packet = buildPacket(state, fromAgent, toAgent, userMessage,
                objectiveHint, traceId, hop, chain, true);
        HandoffSanityResult artifactCheck = validateArtifacts(packet);
        packet = artifactCheck.repairedPacket() != null ? artifactCheck.repairedPacket() : packet;

        // Persist
        state.setUserId(StringUtils.hasText(userId) ? userId : state.getUserId());
        state.setLastAgentType(toAgent);
        state.setHopCount(hop);
        state.setAgentChain(chain);
        state.setLastHandoffPacket(packet);
        state.setLastHandoffNote(humanNote(packet));
        state.setLastHandoffAt(LocalDateTime.now());
        if (StringUtils.hasText(objectiveHint)) {
            state.setActiveGoal(objectiveHint.trim());
        } else if (packet.getMission() != null && StringUtils.hasText(packet.getMission().getObjective())) {
            state.setActiveGoal(packet.getMission().getObjective());
        }
        state.setUpdatedAt(LocalDateTime.now());
        store.save(state);

        HandoffScopeContext.install(packet.getScope());

        if (!artifactCheck.accepted()) {
            log.info("[Handoff] NACK missing artifacts chatId={} missing={}",
                    chatId, artifactCheck.missingArtifactIds());
            return HandoffSanityResult.nackMissingArtifacts(
                    artifactCheck.reason(),
                    artifactCheck.suggestion(),
                    packet,
                    artifactCheck.missingArtifactIds());
        }

        log.info("[Handoff] ACK chatId={} {} -> {} hop={} id={}",
                chatId, fromAgent, toAgent, hop, packet.getMeta().getHandoffId());
        return HandoffSanityResult.ack(packet);
    }

    /**
     * Build a repair injection block when NACK occurs (Request-Reply-Repair).
     */
    public String buildNackRepairInjection(HandoffSanityResult nack) {
        if (nack == null || nack.accepted()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【Handoff NACK — 交接未完全通过健全性检查】\n");
        sb.append("- 原因：").append(nack.reason() != null ? nack.reason() : "unknown").append('\n');
        if (StringUtils.hasText(nack.suggestion())) {
            sb.append("- 修复建议：").append(nack.suggestion()).append('\n');
        }
        if (nack.missingArtifactIds() != null && !nack.missingArtifactIds().isEmpty()) {
            sb.append("- 无效资产 ID 已剥离：").append(String.join(", ", nack.missingArtifactIds())).append('\n');
        }
        sb.append("- 要求：不要编造已剥离的交付物/文件；基于会话共享事实继续服务用户。\n");
        return sb.toString();
    }

    public void clearScope() {
        HandoffScopeContext.clear();
    }

    /**
     * System-layer existence check for artifact IDs (hallucinated reference guard).
     * Missing IDs are stripped; returns NACK when any were invalid.
     */
    public HandoffSanityResult sanitizeArtifacts(HandoffPacket packet) {
        if (packet == null) {
            return HandoffSanityResult.nack("null_packet", "packet required", null);
        }
        return validateArtifacts(packet);
    }

    private HandoffSanityResult validateArtifacts(HandoffPacket packet) {
        if (packet.getArtifacts() == null) {
            return HandoffSanityResult.ack(packet);
        }
        List<String> ids = packet.getArtifacts().getArtifactIds();
        if (ids == null || ids.isEmpty()) {
            packet.getArtifacts().setValidated(true);
            return HandoffSanityResult.ack(packet);
        }
        List<String> valid = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            if (!StringUtils.hasText(id)) {
                continue;
            }
            try {
                if (artifactShelf != null && artifactShelf.get(id).isPresent()) {
                    valid.add(id);
                } else {
                    missing.add(id);
                }
            } catch (Exception e) {
                missing.add(id);
            }
        }
        packet.getArtifacts().setArtifactIds(valid);
        packet.getArtifacts().setValidated(missing.isEmpty());
        if (missing.isEmpty()) {
            return HandoffSanityResult.ack(packet);
        }
        return HandoffSanityResult.nackMissingArtifacts(
                "hallucinated_or_missing_artifacts",
                "交接包中的交付物 ID 不存在，已剥离；请勿引用这些 ID",
                packet,
                missing);
    }

    private HandoffPacket buildPacket(
            SessionSharedState state,
            String fromAgent,
            String toAgent,
            String userMessage,
            String objectiveHint,
            String traceId,
            int hop,
            List<String> chain,
            boolean includeScope) {

        String intent = truncate(userMessage, 120);
        String objective = StringUtils.hasText(objectiveHint)
                ? objectiveHint.trim()
                : inferObjective(toAgent, intent);

        Map<String, String> keyFacts = new LinkedHashMap<>();
        if (state.getFacts() != null) {
            keyFacts.putAll(state.getFacts());
        }

        List<String> appointmentIds = new ArrayList<>();
        if (state.getAppointments() != null) {
            for (SessionSharedState.AppointmentFact a : state.getAppointments()) {
                if (a != null && StringUtils.hasText(a.getAppointmentId())) {
                    appointmentIds.add(a.getAppointmentId());
                }
            }
        }

        List<String> constraints = new ArrayList<>();
        constraints.add("优先采信会话共享状态中的结构化事实，勿假装未知");
        if (!appointmentIds.isEmpty()) {
            constraints.add("已有预约时禁止要求用户重新提供姓名/联系方式");
        }
        constraints.add("不要把完整聊天历史当业务状态；缺失时向用户澄清或查仓库");

        String dod = switch (toAgent != null ? toAgent.toUpperCase() : "") {
            case "CONSULTATION" -> "完成预约查询/创建/修改中用户本轮目标，或明确下一步所需信息";
            case "GENERAL" -> "给出可执行职场建议；若依赖预约/交付物则引用 ID 而非编造";
            case "RESUME" -> "输出可落地的简历/求职改进建议";
            case "NEGOTIATION" -> "给出谈判区间与话术要点";
            default -> "完成本轮用户目标或给出清晰下一步";
        };

        return HandoffPacket.builder()
                .meta(HandoffPacket.Meta.builder()
                        .handoffId("ho_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                        .sourceAgent(fromAgent)
                        .targetAgent(toAgent)
                        .timestamp(LocalDateTime.now())
                        .hopCount(hop)
                        .chain(new ArrayList<>(chain))
                        .traceId(traceId)
                        .priority("normal")
                        .build())
                .mission(HandoffPacket.Mission.builder()
                        .objective(objective)
                        .definitionOfDone(dod)
                        .constraints(constraints)
                        .build())
                .context(HandoffPacket.ContextBlock.builder()
                        .summarySoFar(StringUtils.hasText(state.getLastHandoffNote())
                                ? state.getLastHandoffNote() : "会话继续")
                        .userOriginalIntent(intent)
                        .keyFacts(keyFacts)
                        .backLinkTraceId(traceId)
                        .build())
                .artifacts(HandoffPacket.Artifacts.builder()
                        .appointmentIds(appointmentIds)
                        .artifactIds(new ArrayList<>())
                        .fileUris(new ArrayList<>())
                        .validated(true)
                        .build())
                .scope(includeScope ? new ArrayList<>(defaultScopeFor(toAgent)) : new ArrayList<>())
                .build();
    }

    private static String inferObjective(String toAgent, String intent) {
        if (StringUtils.hasText(intent) && (intent.contains("日程") || intent.contains("预约") || intent.contains("我的约"))) {
            return "查询/确认预约日程";
        }
        if (StringUtils.hasText(toAgent)) {
            return "以 " + toAgent + " 身份处理用户本轮请求";
        }
        return "处理用户本轮请求";
    }

    private static String humanNote(HandoffPacket packet) {
        if (packet == null || packet.getMeta() == null) {
            return "";
        }
        String from = packet.getMeta().getSourceAgent();
        String to = packet.getMeta().getTargetAgent();
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(from) && !Objects.equals(from, to)) {
            sb.append("从 ").append(from).append(" 切换到 ").append(to);
        } else {
            sb.append("当前专家：").append(to);
        }
        if (packet.getMission() != null && StringUtils.hasText(packet.getMission().getObjective())) {
            sb.append("；目标：").append(packet.getMission().getObjective());
        }
        sb.append("；hop=").append(packet.getMeta().getHopCount());
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
