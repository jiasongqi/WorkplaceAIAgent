package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.access.AccessDecisionService;
import com.yupi.yuaiagent.auth.UserQuotaService;
import com.yupi.yuaiagent.calendar.CalendarServiceFactory;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.config.FollowUpTemplateConfig;
import com.yupi.yuaiagent.context.ConversationContextBuilder;
import com.yupi.yuaiagent.memory.MemoryCoordinator;
import com.yupi.yuaiagent.message.ChatMemoryAdapter;
import com.yupi.yuaiagent.message.PersistentMessageRepository;
import com.yupi.yuaiagent.nlu.NluPipeline;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.quality.QualityGuardAgent;
import com.yupi.yuaiagent.quality.QualityModeResolver;
import com.yupi.yuaiagent.quality.QualityReviewRepository;
import com.yupi.yuaiagent.perception.PerceptionHybridContextService;
import com.yupi.yuaiagent.rag.PipelineRagAdvisorFactory;
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
        PipelineRagAdvisorFactory pipelineRagAdvisorFactory,
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
        com.yupi.yuaiagent.workflow.dag.DagCompiler dagCompiler,
        com.yupi.yuaiagent.workflow.dag.DagWorkflowExecutor dagWorkflowExecutor,
        boolean workflowDagEnabled,
        AccessDecisionService accessDecisionService,
        Executor agentExecutor,
        MemoryCoordinator memoryCoordinator,
        com.yupi.yuaiagent.guard.PromptInjectionDetector promptInjectionDetector,
        com.yupi.yuaiagent.agent.collaboration.AgentCollaborationCoordinator collaborationCoordinator,
        com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService,
        com.yupi.yuaiagent.metrics.AgentExecutionMetrics agentExecutionMetrics,
        com.yupi.yuaiagent.hitl.HumanApprovalService humanApprovalService,
        com.yupi.yuaiagent.companion.UserCompanionService userCompanionService,
        com.yupi.yuaiagent.service.DigitalEmployeeAppService digitalEmployeeAppService,
        com.yupi.yuaiagent.service.ExpertPackAppService expertPackAppService,
        com.yupi.yuaiagent.sessionstate.SessionSharedStateService sessionSharedStateService,
        com.yupi.yuaiagent.hitl.HumanHandoffService humanHandoffService,
        com.yupi.yuaiagent.agent.manifest.AgentManifestRegistry agentManifestRegistry,
        com.yupi.yuaiagent.artifact.ArtifactPublisher artifactPublisher,
        com.yupi.yuaiagent.artifact.recall.ArtifactRecallService artifactRecallService,
        com.yupi.yuaiagent.artifact.adoption.ArtifactAdoptionService artifactAdoptionService,
        com.yupi.yuaiagent.agent.data.DataAnalystAgent dataAnalystAgent,
        com.yupi.yuaiagent.agent.data.CareerCoachAgent careerCoachAgent,
        com.yupi.yuaiagent.agent.data.ProfileCuratorAgent profileCuratorAgent,
        com.yupi.yuaiagent.agent.data.PromotionPlannerAgent promotionPlannerAgent,
        com.yupi.yuaiagent.agent.data.LearningResourceRecommenderAgent learningResourceRecommenderAgent,
        UserQuotaService userQuotaService,
        PerceptionHybridContextService perceptionHybridContextService
) {
}
