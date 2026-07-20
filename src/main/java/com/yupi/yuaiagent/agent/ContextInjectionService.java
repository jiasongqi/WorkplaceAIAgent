package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactQuery;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.message.PersistentMessageRepository;
import com.yupi.yuaiagent.profile.UserProfileService;
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
 * user profile, ready artifacts, cross-agent conversation history.
 *
 * <p>Extracted from OrchestratorAgent to reduce god-class complexity.
 *
 * @author jsq
 */
@Slf4j
public class ContextInjectionService {

    private final UserProfileService userProfileService;
    private final ArtifactShelf artifactShelf;
    private final PersistentMessageRepository messageRepository;
    private final ChatMemoryManager chatMemoryManager;
    private final TraceRecorder traceRecorder;
    private final com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService;

    public ContextInjectionService(UserProfileService userProfileService,
                                   ArtifactShelf artifactShelf,
                                   PersistentMessageRepository messageRepository,
                                   ChatMemoryManager chatMemoryManager,
                                   TraceRecorder traceRecorder) {
        this(userProfileService, artifactShelf, messageRepository, chatMemoryManager, traceRecorder, null);
    }

    public ContextInjectionService(UserProfileService userProfileService,
                                   ArtifactShelf artifactShelf,
                                   PersistentMessageRepository messageRepository,
                                   ChatMemoryManager chatMemoryManager,
                                   TraceRecorder traceRecorder,
                                   com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService) {
        this.userProfileService = userProfileService;
        this.artifactShelf = artifactShelf;
        this.messageRepository = messageRepository;
        this.chatMemoryManager = chatMemoryManager;
        this.traceRecorder = traceRecorder;
        this.reflexionService = reflexionService;
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
     * Builds the combined injection context (profile + artifacts + cross-agent history +
     * per-intent reflexion failure memory).
     *
     * @param taskType the resolved routing intent name (e.g. "RESUME", "NEGOTIATION"), used to
     *                 scope {@link com.yupi.yuaiagent.agent.reflexion.ReflexionService} lookups
     *                 so a failure recorded for one specialist doesn't leak into another's prompt.
     * @return combined context string, or empty if nothing to inject
     */
    public String buildCombinedInjection(String userId, String chatId, TraceContext traceCtx, String taskType) {
        // Profile injection
        TraceSpan profileSpan = traceRecorder.startSpan(traceCtx, TraceStepType.PROFILE_INJECTION, "画像注入");
        String profileInjection = StringUtils.hasText(userId)
                ? userProfileService.buildPromptInjection(userId) : "";
        traceRecorder.putMetadata(profileSpan, "hasProfile", String.valueOf(StringUtils.hasText(profileInjection)));
        traceRecorder.endSpan(traceCtx, profileSpan);

        // Artifact query
        TraceSpan artifactQuerySpan = traceRecorder.startSpan(traceCtx, TraceStepType.ARTIFACT_QUERY, "交付物查询");
        List<Artifact> readyArtifacts = queryReadyArtifacts(userId, chatId);
        String artifactContext = buildArtifactContext(readyArtifacts);
        traceRecorder.putMetadata(artifactQuerySpan, "foundCount", String.valueOf(readyArtifacts.size()));
        traceRecorder.endSpan(traceCtx, artifactQuerySpan);

        // Merge
        String combined = mergeInjection(profileInjection, artifactContext);
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

        // Mark consumed
        TraceSpan consumeSpan = traceRecorder.startSpan(traceCtx, TraceStepType.ARTIFACT_CONSUME, "交付物消费");
        markArtifactsConsumed(readyArtifacts);
        traceRecorder.putMetadata(consumeSpan, "consumedCount", String.valueOf(readyArtifacts.size()));
        traceRecorder.endSpan(traceCtx, consumeSpan);

        return combined;
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

    private List<Artifact> queryReadyArtifacts(String userId, String chatId) {
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(chatId)) {
            return List.of();
        }
        try {
            return artifactShelf.query(ArtifactQuery.builder()
                    .userId(StringUtils.hasText(userId) ? userId : null)
                    .chatId(StringUtils.hasText(chatId) ? chatId : null)
                    .status(ArtifactStatus.READY)
                    .build());
        } catch (Exception e) {
            log.error("查询 READY 交付物失败，降级为不注入交付物，userId={}, chatId={}", userId, chatId, e);
            return List.of();
        }
    }

    private String buildArtifactContext(List<Artifact> readyArtifacts) {
        if (readyArtifacts == null || readyArtifacts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【参考交付物】以下是与本次对话相关的已就绪交付物，请在回答时参考：\n");
        for (Artifact artifact : readyArtifacts) {
            String title = StringUtils.hasText(artifact.getTitle()) ? artifact.getTitle() : "（无标题）";
            String content = artifact.getContent() != null ? artifact.getContent() : "";
            sb.append("- ").append(title).append("：").append(content).append("\n");
        }
        log.info("注入 {} 个 READY 交付物到子 Agent 上下文", readyArtifacts.size());
        return sb.toString();
    }

    private String buildCrossAgentContext(String chatId) {
        try {
            var messages = messageRepository.findByChatId(chatId);
            if (messages == null || messages.isEmpty()) return "";

            int from = Math.max(0, messages.size() - 10);
            var recent = messages.subList(from, messages.size());

            StringBuilder sb = new StringBuilder("【近期对话记录】以下是本会话中与其他顾问的对话摘要，请参考上下文回答：\n");
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

    private void markArtifactsConsumed(List<Artifact> consumedArtifacts) {
        if (consumedArtifacts == null || consumedArtifacts.isEmpty()) {
            return;
        }
        for (Artifact artifact : consumedArtifacts) {
            String artifactId = artifact.getArtifactId();
            if (!StringUtils.hasText(artifactId)) {
                continue;
            }
            try {
                boolean marked = artifactShelf.markConsumed(artifactId);
                if (marked) {
                    log.info("交付物已标记为 CONSUMED，artifactId={}", artifactId);
                } else {
                    log.warn("标记交付物消费失败（可能已不存在），artifactId={}", artifactId);
                }
            } catch (Exception e) {
                log.error("标记交付物消费异常，artifactId={}", artifactId, e);
            }
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
}
