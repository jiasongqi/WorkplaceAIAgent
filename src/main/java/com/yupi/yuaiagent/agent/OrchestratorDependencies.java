package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.access.AccessDecisionService;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.config.FollowUpTemplateConfig;
import com.yupi.yuaiagent.context.ConversationContextBuilder;
import com.yupi.yuaiagent.memory.MemoryCoordinator;
import com.yupi.yuaiagent.message.ChatMemoryAdapter;
import com.yupi.yuaiagent.message.PersistentMessageRepository;
import com.yupi.yuaiagent.nlu.DataQueryRouter;
import com.yupi.yuaiagent.nlu.NluPipeline;
import com.yupi.yuaiagent.quality.QualityGuardAgent;
import com.yupi.yuaiagent.quality.QualityModeResolver;
import com.yupi.yuaiagent.quality.QualityReviewRepository;
import com.yupi.yuaiagent.rag.QueryRewriter;
import com.yupi.yuaiagent.repository.AppointmentRepository;
import com.yupi.yuaiagent.skill.SkillExecutor;
import com.yupi.yuaiagent.skill.SkillRegistry;
import com.yupi.yuaiagent.trace.TraceRecorder;
import com.yupi.yuaiagent.trace.TraceRepository;
import com.yupi.yuaiagent.validation.InfoValidator;
import com.yupi.yuaiagent.workflow.WorkflowMatcher;
import com.yupi.yuaiagent.workflow.WorkflowRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.concurrent.Executor;

/**
 * Aggregated dependencies for {@link OrchestratorAgent}.
 * <p>
 * Reduces constructor parameter count from 27 to a single record.
 * Spring auto-discovers all beans and injects them here.
 *
 * @author jsq
 */
public record OrchestratorDependencies(
        ChatModel chatModel,
        VectorStore vectorStore,
        ToolCallback[] tools,
        QueryRewriter queryRewriter,
        ChatMemoryManager chatMemoryManager,
        FollowUpTemplateConfig templateConfig,
        InfoValidator infoValidator,
        CalendarServiceFactory calendarServiceFactory,
        AppointmentRepository appointmentRepository,
        SkillExecutor skillExecutor,
        SkillRegistry skillRegistry,
        UserProfileService userProfileService,
        com.yupi.yuaiagent.artifact.ArtifactShelf artifactShelf,
        TraceRecorder traceRecorder,
        TraceRepository traceRepository,
        ChatMemoryAdapter chatMemoryAdapter,
        QualityGuardAgent qualityGuardAgent,
        QualityModeResolver qualityModeResolver,
        QualityReviewRepository qualityReviewRepository,
        PersistentMessageRepository messageRepository,
        NluPipeline nluPipeline,
        DataQueryRouter dataQueryRouter,
        WorkflowMatcher workflowMatcher,
        WorkflowRegistry workflowRegistry,
        ConversationContextBuilder contextBuilder,
        TaskExecutor taskExecutor,
        ResultAggregator resultAggregator,
        AccessDecisionService accessDecisionService,
        Executor agentExecutor,
        MemoryCoordinator memoryCoordinator,
        com.yupi.yuaiagent.guard.PromptInjectionDetector promptInjectionDetector
) {
}
