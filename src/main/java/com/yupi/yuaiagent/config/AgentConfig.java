package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.access.AccessDecisionService;
import com.yupi.yuaiagent.agent.DataQueryRouter;
import com.yupi.yuaiagent.agent.OrchestratorAgent;
import com.yupi.yuaiagent.agent.OrchestratorDependencies;
import com.yupi.yuaiagent.agent.ResultAggregator;
import com.yupi.yuaiagent.agent.TaskExecutor;
import com.yupi.yuaiagent.agent.data.CareerCoachAgent;
import com.yupi.yuaiagent.agent.data.DataAnalystAgent;
import com.yupi.yuaiagent.agent.data.LearningResourceRecommenderAgent;
import com.yupi.yuaiagent.agent.data.ProfileCuratorAgent;
import com.yupi.yuaiagent.agent.data.PromotionPlannerAgent;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.calendar.CalendarServiceFactory;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.context.ConversationContextBuilder;
import com.yupi.yuaiagent.message.ChatMemoryAdapter;
import com.yupi.yuaiagent.memory.MemoryCoordinator;
import com.yupi.yuaiagent.nlu.*;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.quality.*;
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
import com.yupi.yuaiagent.auth.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

/**
 * Agent related Bean configuration.
 *
 * @author jsq
 */
@Configuration
@EnableConfigurationProperties({CalendarConfig.class, CompressionConfig.class, AuthProperties.class})
public class AgentConfig {

    /**
     * Conversation state store — in-memory default for dev/test.
     * For production, create a RedisConversationStateStore bean instead.
     */
    @Bean
    public ConversationStateStore conversationStateStore() {
        return new InMemoryConversationStateStore();
    }

    /**
     * NLU Pipeline — single LLM call for intent extraction, routing, and clarification.
     */
    @Bean
    public NluPipeline nluPipeline(
            ConversationStateStore stateStore,
            AliasResolver aliasResolver,
            UnifiedNluExtractor extractor,
            IntentReranker intentReranker,
            IntentAmbiguityDetector ambiguityDetector,
            RouteTemplate routeTemplate,
            @org.springframework.beans.factory.annotation.Qualifier("ruleContextShiftDetector") ContextShiftDetector shiftDetector,
            IntentRequirementRegistry requirementRegistry,
            ClarificationHandler clarificationHandler) {
        return new NluPipeline(stateStore, aliasResolver, extractor, intentReranker,
                ambiguityDetector, routeTemplate, shiftDetector, requirementRegistry, clarificationHandler);
    }

    /**
     * Data query router bean — handles QUERY_DATA intent without LLM call.
     */
    @Bean
    public DataQueryRouter dataQueryRouter() {
        return new DataQueryRouter();
    }

    @Bean
    public OrchestratorAgent orchestratorAgent(
            ChatModel dashscopeChatModel,
            @org.springframework.beans.factory.annotation.Qualifier("aiChatVectorStore") VectorStore aiChatVectorStore,
            ToolCallback[] allTools,
            QueryRewriter queryRewriter,
            ChatMemoryManager chatMemoryManager,
            FollowUpTemplateConfig followUpTemplateConfig,
            InfoValidator infoValidator,
            CalendarServiceFactory calendarServiceFactory,
            AppointmentRepository appointmentRepository,
            SkillExecutor skillExecutor,
            SkillRegistry skillRegistry,
            UserProfileService userProfileService,
            ArtifactShelf artifactShelf,
            TraceRecorder traceRecorder,
            TraceRepository traceRepository,
            ChatMemoryAdapter chatMemoryAdapter,
            QualityGuardAgent qualityGuardAgent,
            QualityModeResolver qualityModeResolver,
            QualityReviewRepository qualityReviewRepository,
            com.yupi.yuaiagent.message.PersistentMessageRepository messageRepository,
            NluPipeline nluPipeline,
            DataQueryRouter dataQueryRouter,
            WorkflowMatcher workflowMatcher,
            WorkflowRegistry workflowRegistry,
            ConversationContextBuilder contextBuilder,
            TaskExecutor taskExecutor,
            ResultAggregator resultAggregator,
            AccessDecisionService accessDecisionService,
            @org.springframework.beans.factory.annotation.Qualifier("agentExecutor") Executor agentExecutor,
            @org.springframework.lang.Nullable MemoryCoordinator memoryCoordinator,
            com.yupi.yuaiagent.guard.PromptInjectionDetector promptInjectionDetector,
            com.yupi.yuaiagent.agent.collaboration.AgentCollaborationCoordinator collaborationCoordinator,
            com.yupi.yuaiagent.agent.reflexion.ReflexionService reflexionService,
            com.yupi.yuaiagent.metrics.AgentExecutionMetrics agentExecutionMetrics,
            com.yupi.yuaiagent.hitl.HumanApprovalService humanApprovalService) {
        var deps = new OrchestratorDependencies(
                dashscopeChatModel, aiChatVectorStore, allTools, queryRewriter, chatMemoryManager,
                followUpTemplateConfig, infoValidator, calendarServiceFactory, appointmentRepository,
                skillExecutor, skillRegistry, userProfileService, artifactShelf,
                traceRecorder, traceRepository, chatMemoryAdapter,
                qualityGuardAgent, qualityModeResolver, qualityReviewRepository, messageRepository,
                nluPipeline, dataQueryRouter,
                workflowMatcher, workflowRegistry, contextBuilder, taskExecutor, resultAggregator,
                accessDecisionService, agentExecutor, memoryCoordinator, promptInjectionDetector,
                collaborationCoordinator, reflexionService, agentExecutionMetrics, humanApprovalService);
        return new OrchestratorAgent(deps);
    }

    @Bean
    public DataAnalystAgent dataAnalystAgent(
            ChatModel dashscopeChatModel,
            ChatMemoryManager chatMemoryManager,
            ArtifactShelf artifactShelf) {
        return new DataAnalystAgent(dashscopeChatModel, chatMemoryManager, artifactShelf);
    }

    @Bean
    public CareerCoachAgent careerCoachAgent(
            ChatModel dashscopeChatModel,
            ChatMemoryManager chatMemoryManager,
            ArtifactShelf artifactShelf) {
        return new CareerCoachAgent(dashscopeChatModel, chatMemoryManager, artifactShelf);
    }

    @Bean
    public ProfileCuratorAgent profileCuratorAgent(
            ChatModel dashscopeChatModel,
            ChatMemoryManager chatMemoryManager,
            ArtifactShelf artifactShelf) {
        return new ProfileCuratorAgent(dashscopeChatModel, chatMemoryManager, artifactShelf);
    }

    @Bean
    public PromotionPlannerAgent promotionPlannerAgent(
            ChatModel dashscopeChatModel,
            ChatMemoryManager chatMemoryManager,
            ArtifactShelf artifactShelf) {
        return new PromotionPlannerAgent(dashscopeChatModel, chatMemoryManager, artifactShelf);
    }

    @Bean
    public LearningResourceRecommenderAgent learningResourceRecommenderAgent(
            ChatModel dashscopeChatModel,
            ChatMemoryManager chatMemoryManager,
            UserProfileService userProfileService,
            ArtifactShelf artifactShelf) {
        return new LearningResourceRecommenderAgent(
                dashscopeChatModel, chatMemoryManager, userProfileService, artifactShelf);
    }
}
