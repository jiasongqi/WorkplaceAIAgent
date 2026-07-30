package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.goal.GoalAnchor;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.ArtifactTypeCatalog;
import com.yupi.yuaiagent.artifact.recall.ArtifactRecallService;
import com.yupi.yuaiagent.artifact.recall.RecallResult;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.companion.UserCompanionService;
import com.yupi.yuaiagent.message.PersistentMessageRepository;
import com.yupi.yuaiagent.perception.PerceptionHybridContextService;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.service.DigitalEmployeeAppService;
import com.yupi.yuaiagent.sessionstate.SessionSharedStateService;
import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceRecorder;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Handles context injection into agent prompts:
 * user profile, companion persona, digital employee, ready artifacts, cross-agent conversation history.
 *
 * <p>Extracted from OrchestratorAgent to reduce god-class complexity.
 *
 * @author jsq
 */
@Slf4j
public class ContextInjectionService {

    private final UserProfileService userProfileService;
    private final PersistentMessageRepository messageRepository;
    private final ChatMemoryManager chatMemoryManager;
    private final TraceRecorder traceRecorder;
    private final com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService;
    private final UserCompanionService userCompanionService;
    private final DigitalEmployeeAppService digitalEmployeeAppService;
    private final SessionSharedStateService sessionSharedStateService;
    private final ArtifactRecallService artifactRecallService;
    private final PerceptionHybridContextService perceptionHybridContextService;

    public ContextInjectionService(UserProfileService userProfileService,
                                   ArtifactShelf artifactShelf,
                                   PersistentMessageRepository messageRepository,
                                   ChatMemoryManager chatMemoryManager,
                                   TraceRecorder traceRecorder) {
        this(userProfileService, artifactShelf, messageRepository, chatMemoryManager, traceRecorder,
                null, null, null, null, null);
    }

    public ContextInjectionService(UserProfileService userProfileService,
                                   ArtifactShelf artifactShelf,
                                   PersistentMessageRepository messageRepository,
                                   ChatMemoryManager chatMemoryManager,
                                   TraceRecorder traceRecorder,
                                   com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService) {
        this(userProfileService, artifactShelf, messageRepository, chatMemoryManager, traceRecorder,
                reflexionService, null, null, null, null);
    }

    public ContextInjectionService(UserProfileService userProfileService,
                                   ArtifactShelf artifactShelf,
                                   PersistentMessageRepository messageRepository,
                                   ChatMemoryManager chatMemoryManager,
                                   TraceRecorder traceRecorder,
                                   com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService,
                                   UserCompanionService userCompanionService,
                                   DigitalEmployeeAppService digitalEmployeeAppService) {
        this(userProfileService, artifactShelf, messageRepository, chatMemoryManager, traceRecorder,
                reflexionService, userCompanionService, digitalEmployeeAppService, null, null);
    }

    public ContextInjectionService(UserProfileService userProfileService,
                                   ArtifactShelf artifactShelf,
                                   PersistentMessageRepository messageRepository,
                                   ChatMemoryManager chatMemoryManager,
                                   TraceRecorder traceRecorder,
                                   com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService,
                                   UserCompanionService userCompanionService,
                                   DigitalEmployeeAppService digitalEmployeeAppService,
                                   SessionSharedStateService sessionSharedStateService) {
        this(userProfileService, artifactShelf, messageRepository, chatMemoryManager, traceRecorder,
                reflexionService, userCompanionService, digitalEmployeeAppService,
                sessionSharedStateService, null, null);
    }

    public ContextInjectionService(UserProfileService userProfileService,
                                   ArtifactShelf artifactShelf,
                                   PersistentMessageRepository messageRepository,
                                   ChatMemoryManager chatMemoryManager,
                                   TraceRecorder traceRecorder,
                                   com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService,
                                   UserCompanionService userCompanionService,
                                   DigitalEmployeeAppService digitalEmployeeAppService,
                                   SessionSharedStateService sessionSharedStateService,
                                   ArtifactRecallService artifactRecallService) {
        this(userProfileService, artifactShelf, messageRepository, chatMemoryManager, traceRecorder,
                reflexionService, userCompanionService, digitalEmployeeAppService,
                sessionSharedStateService, artifactRecallService, null);
    }

    public ContextInjectionService(UserProfileService userProfileService,
                                   ArtifactShelf artifactShelf,
                                   PersistentMessageRepository messageRepository,
                                   ChatMemoryManager chatMemoryManager,
                                   TraceRecorder traceRecorder,
                                   com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService,
                                   UserCompanionService userCompanionService,
                                   DigitalEmployeeAppService digitalEmployeeAppService,
                                   SessionSharedStateService sessionSharedStateService,
                                   ArtifactRecallService artifactRecallService,
                                   PerceptionHybridContextService perceptionHybridContextService) {
        this.userProfileService = userProfileService;
        this.messageRepository = messageRepository;
        this.chatMemoryManager = chatMemoryManager;
        this.traceRecorder = traceRecorder;
        this.reflexionService = reflexionService;
        this.userCompanionService = userCompanionService;
        this.digitalEmployeeAppService = digitalEmployeeAppService;
        this.sessionSharedStateService = sessionSharedStateService;
        this.perceptionHybridContextService = perceptionHybridContextService;
        this.artifactRecallService = artifactRecallService != null
                ? artifactRecallService
                : new ArtifactRecallService(artifactShelf, ArtifactTypeCatalog.defaults(), 3, 1200);
    }

    /**
     * Builds the combined injection context (profile + artifacts + cross-agent history).
     * Uses "GENERAL" as the reflexion task type — prefer the overload that accepts an
     * explicit {@code taskType} once the routed intent is known.
     *
     * @return combined context string, or empty if nothing to inject
     */
    public String buildCombinedInjection(String userId, String chatId, TraceContext traceCtx) {
        return buildCombinedInjection(userId, chatId, traceCtx, "GENERAL");
    }

    /**
     * Builds the combined injection context (profile + companion + digital employee + artifacts +
     * cross-agent history + per-intent reflexion failure memory).
     *
     * @param taskType the resolved routing intent name (e.g. "RESUME", "NEGOTIATION"), used to
     *                 scope {@link com.yupi.yuaiagent.agent.reflexion.ReflexionService} lookups
     *                 so a failure recorded for one specialist doesn't leak into another's prompt.
     * @return combined context string, or empty if nothing to inject
     */
    public String buildCombinedInjection(String userId, String chatId, TraceContext traceCtx, String taskType) {
        return buildCombinedInjectionResult(userId, chatId, traceCtx, taskType, "").text();
    }

    /**
     * Builds context and returns the artifact ids offered to the target Agent.
     */
    public InjectionResult buildCombinedInjectionResult(String userId, String chatId,
                                                        TraceContext traceCtx, String taskType,
                                                        String queryText) {
        // Goal anchor FIRST — re-injected every turn to prevent long-context forgetting
        String activeGoal = "";
        if (sessionSharedStateService != null && StringUtils.hasText(chatId)) {
            try {
                activeGoal = sessionSharedStateService.getOrCreate(chatId, userId).getActiveGoal();
            } catch (Exception e) {
                log.debug("Active goal lookup skipped: {}", e.getMessage());
            }
        }
        String goalBlock = GoalAnchor.buildBlock(activeGoal, queryText, taskType);

        // Profile injection
        TraceSpan profileSpan = traceRecorder.startSpan(traceCtx, TraceStepType.PROFILE_INJECTION, "画像注入");
        String profileInjection = StringUtils.hasText(userId)
                ? userProfileService.buildPromptInjection(userId) : "";
        traceRecorder.putMetadata(profileSpan, "hasProfile", String.valueOf(StringUtils.hasText(profileInjection)));
        traceRecorder.putMetadata(profileSpan, "hasGoalAnchor", String.valueOf(StringUtils.hasText(goalBlock)));
        traceRecorder.endSpan(traceCtx, profileSpan);

        String companionInjection = "";
        if (userCompanionService != null && StringUtils.hasText(userId)) {
            try {
                companionInjection = userCompanionService.buildPromptInjection(userId);
            } catch (Exception e) {
                log.debug("Companion injection skipped: {}", e.getMessage());
            }
        }

        String digitalEmployeeInjection = "";
        if (digitalEmployeeAppService != null && StringUtils.hasText(userId)) {
            try {
                digitalEmployeeInjection = digitalEmployeeAppService.buildActiveInjection(userId);
            } catch (Exception e) {
                log.debug("Digital employee injection skipped: {}", e.getMessage());
            }
        }

        String sharedStateInjection = "";
        if (sessionSharedStateService != null && StringUtils.hasText(chatId)) {
            try {
                sharedStateInjection = sessionSharedStateService.buildPromptInjection(chatId, userId);
            } catch (Exception e) {
                log.debug("Shared session state injection skipped: {}", e.getMessage());
            }
        }

        // Artifact query
        TraceSpan artifactQuerySpan = traceRecorder.startSpan(traceCtx, TraceStepType.ARTIFACT_QUERY, "交付物查询");
        RecallResult recallResult;
        try {
            recallResult = artifactRecallService.recall(userId, chatId, taskType, queryText);
        } catch (Exception e) {
            log.warn("交付物召回失败，降级为空上下文，userId={}, chatId={}", userId, chatId, e);
            recallResult = RecallResult.empty();
        }
        String artifactContext = recallResult.injectionText();
        traceRecorder.putMetadata(artifactQuerySpan, "foundCount",
                String.valueOf(recallResult.offeredArtifactIds().size()));
        traceRecorder.endSpan(traceCtx, artifactQuerySpan);

        // Merge: Goal → persona → shared structured state → artifacts → cross-agent transcript
        String combined = mergeInjection(goalBlock, profileInjection);
        combined = mergeInjection(combined, companionInjection);
        combined = mergeInjection(combined, digitalEmployeeInjection);
        combined = mergeInjection(combined, sharedStateInjection);
        combined = mergeInjection(combined, artifactContext);

        if (perceptionHybridContextService != null && StringUtils.hasText(queryText) && StringUtils.hasText(chatId)) {
            try {
                String hybrid = perceptionHybridContextService.buildHybridContext(chatId, userId, queryText);
                combined = mergeInjection(combined, hybrid);
            } catch (Exception e) {
                log.debug("Perception hybrid injection skipped: {}", e.getMessage());
            }
        }

        String crossAgentContext = buildCrossAgentContext(chatId);
        if (StringUtils.hasText(crossAgentContext)) {
            combined = mergeInjection(combined, crossAgentContext);
        }

        if (reflexionService != null && StringUtils.hasText(userId)) {
            try {
                String failureCtx = reflexionService.getFailureContext(
                        userId, StringUtils.hasText(taskType) ? taskType : "GENERAL");
                if (StringUtils.hasText(failureCtx)) {
                    combined = mergeInjection(combined, failureCtx);
                }
            } catch (Exception e) {
                log.debug("Reflexion injection skipped: {}", e.getMessage());
            }
        }

        return new InjectionResult(combined, recallResult.offeredArtifactIds());
    }

    /**
     * Triggers async profile update after conversation ends.
     */
    public void triggerProfileUpdate(String userId, String memoryType, String chatId, TraceContext traceCtx) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        TraceSpan profileUpdateSpan = traceRecorder.startSpan(traceCtx, TraceStepType.PROFILE_UPDATE, "画像异步更新");
        try {
            ChatMemory memory = chatMemoryManager.getMemory(memoryType);
            List<Message> conversation = memory.get(chatId);
            userProfileService.updateAsync(userId, conversation);
            traceRecorder.endSpan(traceCtx, profileUpdateSpan);
        } catch (Exception e) {
            log.error("对话结束触发画像更新失败，userId={}, chatId={}", userId, chatId, e);
            traceRecorder.failSpan(traceCtx, profileUpdateSpan, e.getMessage());
        }
    }

    /**
     * Syncs cross-agent conversation history into the target agent's ChatMemory.
     */
    public void syncCrossAgentMemory(String chatId, String targetAgentType, String previousAgentType) {
        try {
            var persistentMessages = messageRepository.findByChatId(chatId);
            if (persistentMessages == null || persistentMessages.isEmpty()) {
                return;
            }

            ChatMemory targetMemory = chatMemoryManager.getMemory(targetAgentType);
            List<Message> existing = targetMemory.get(chatId);
            if (existing != null && !existing.isEmpty()) {
                if (existing.size() >= persistentMessages.size()) {
                    log.debug("[CrossAgentMemory] target {} already has {} messages (persistent: {}), skip sync",
                            targetAgentType, existing.size(), persistentMessages.size());
                    return;
                }
            }

            // Check previous agent's memory for un-persisted messages
            if (previousAgentType != null && !previousAgentType.equals(targetAgentType)) {
                ChatMemory prevMemory = chatMemoryManager.getMemory(previousAgentType);
                List<Message> prevMessages = prevMemory.get(chatId);
                if (prevMessages != null && prevMessages.size() > persistentMessages.size()) {
                    log.info("[CrossAgentMemory] found {} un-persisted messages in {} memory, using as source",
                            prevMessages.size(), previousAgentType);
                    targetMemory.clear(chatId);
                    targetMemory.add(chatId, prevMessages);
                    log.info("[CrossAgentMemory] synced {} messages from {} to {} for chatId={}",
                            prevMessages.size(), previousAgentType, targetAgentType, chatId);
                    return;
                }
            }

            // Convert PersistentChatMessage → Spring AI Message
            List<Message> history = persistentMessages.stream()
                    .map(pm -> switch (pm.getRole()) {
                        case "user" -> (Message) new org.springframework.ai.chat.messages.UserMessage(pm.getContent());
                        case "assistant" -> (Message) new org.springframework.ai.chat.messages.AssistantMessage(pm.getContent());
                        case "system" -> (Message) new org.springframework.ai.chat.messages.SystemMessage(pm.getContent());
                        default -> (Message) new org.springframework.ai.chat.messages.UserMessage(pm.getContent());
                    })
                    .toList();

            targetMemory.clear(chatId);
            targetMemory.add(chatId, history);
            log.info("[CrossAgentMemory] synced {} messages to {} memory for chatId={}",
                    history.size(), targetAgentType, chatId);
        } catch (Exception e) {
            log.warn("[CrossAgentMemory] failed to sync for chatId={}, target={}: {}",
                    chatId, targetAgentType, e.getMessage());
        }
    }

    // ── Private helpers ──

    private String buildCrossAgentContext(String chatId) {
        try {
            var messages = messageRepository.findByChatId(chatId);
            if (messages == null || messages.isEmpty()) return "";

            int from = Math.max(0, messages.size() - 10);
            var recent = messages.subList(from, messages.size());

            StringBuilder sb = new StringBuilder(
                    "【近期对话记录】以下是本会话摘要（优先级低于 Shared Session State 中的结构化事实；冲突时以事实/预约编号为准）：\n");
            for (var msg : recent) {
                String role = "user".equals(msg.getRole()) ? "用户" : "AI";
                String content = msg.getContent();
                if (content != null && content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                sb.append(role).append("：").append(content).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("构建跨 Agent 上下文失败", e);
            return "";
        }
    }

    private String mergeInjection(String profileInjection, String artifactContext) {
        boolean hasProfile = StringUtils.hasText(profileInjection);
        boolean hasArtifact = StringUtils.hasText(artifactContext);
        if (hasProfile && hasArtifact) {
            return profileInjection + "\n" + artifactContext;
        }
        if (hasProfile) {
            return profileInjection;
        }
        if (hasArtifact) {
            return artifactContext;
        }
        return "";
    }

    public record InjectionResult(String text, List<String> offeredArtifactIds) {
        public InjectionResult {
            text = text == null ? "" : text;
            offeredArtifactIds = offeredArtifactIds == null ? List.of() : List.copyOf(offeredArtifactIds);
        }
    }
}
