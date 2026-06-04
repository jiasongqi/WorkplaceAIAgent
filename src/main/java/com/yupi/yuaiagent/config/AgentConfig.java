package com.yupi.yuaiagent.config;

import com.yupi.yuaiagent.agent.OrchestratorAgent;
import com.yupi.yuaiagent.agent.data.CareerCoachAgent;
import com.yupi.yuaiagent.agent.data.DataAnalystAgent;
import com.yupi.yuaiagent.agent.data.LearningResourceRecommenderAgent;
import com.yupi.yuaiagent.agent.data.ProfileCuratorAgent;
import com.yupi.yuaiagent.agent.data.PromotionPlannerAgent;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.calendar.CalendarServiceFactory;
import com.yupi.yuaiagent.chatmemory.ChatMemoryManager;
import com.yupi.yuaiagent.profile.UserProfileService;
import com.yupi.yuaiagent.quality.*;
import com.yupi.yuaiagent.rag.QueryRewriter;
import com.yupi.yuaiagent.repository.AppointmentRepository;
import com.yupi.yuaiagent.skill.SkillExecutor;
import com.yupi.yuaiagent.skill.SkillRegistry;
import com.yupi.yuaiagent.trace.TraceRecorder;
import com.yupi.yuaiagent.trace.TraceRepository;
import com.yupi.yuaiagent.validation.InfoValidator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 相关 Bean 配置
 *
 * <p>将 OrchestratorAgent 注册为单例 Bean，避免每次请求都重建全部子 Agent、
 * ChatClient 与 RAG Advisor。OrchestratorAgent 本身无请求级可变状态
 * （会话状态按 chatId 存于 ChatMemory），因此单例是线程安全的。
 *
 * <p>通过 {@link EnableConfigurationProperties} 装配日历与记忆压缩两类配置：
 * <ul>
 *   <li>{@link CalendarConfig}：绑定 {@code calendar.*}，集中管理提供商与飞书/钉钉凭证
 *       （Requirements 2.1）；</li>
 *   <li>{@link CompressionConfig}：绑定 {@code chat.memory.compression.*}，集中管理
 *       Token / 轮数阈值与保留轮数（Requirements 3.3 / 4.1 / 4.2）。</li>
 * </ul>
 * 追问模板配置 {@link FollowUpTemplateConfig} 自身为 {@code @Component + @ConfigurationProperties}，
 * 由组件扫描装配并支持热更新（Requirements 6.4），随构造参数注入到下游 Agent。
 *
 * @author jsq
 */
@Configuration
@EnableConfigurationProperties({CalendarConfig.class, CompressionConfig.class})
public class AgentConfig {

    @Bean
    public OrchestratorAgent orchestratorAgent(
            ChatModel dashscopeChatModel,
            VectorStore aiChatVectorStore,
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
            QualityGuardAgent qualityGuardAgent,
            QualityModeResolver qualityModeResolver,
            QualityReviewRepository qualityReviewRepository) {
        return new OrchestratorAgent(
                dashscopeChatModel, aiChatVectorStore, allTools, queryRewriter, chatMemoryManager,
                followUpTemplateConfig, infoValidator, calendarServiceFactory, appointmentRepository,
                skillExecutor, skillRegistry, userProfileService, artifactShelf,
                traceRecorder, traceRepository,
                qualityGuardAgent, qualityModeResolver, qualityReviewRepository);
    }

    /**
     * 数据分析师 Agent Bean。
     *
     * <p>作为第一期落地的数据员工，依赖大模型、对话记忆管理器与共享交付物货架，
     * 产出结构化数据分析报告并放入货架。与其它 Agent 一致注册为单例。
     */
    @Bean
    public DataAnalystAgent dataAnalystAgent(
            ChatModel dashscopeChatModel,
            ChatMemoryManager chatMemoryManager,
            ArtifactShelf artifactShelf) {
        return new DataAnalystAgent(dashscopeChatModel, chatMemoryManager, artifactShelf);
    }

    /**
     * 岗位辅导数据员工 Bean（P2 扩展数据员工）。
     * 基于对话历史或上传文档产出岗位辅导建议交付物并放入货架。
     */
    @Bean
    public CareerCoachAgent careerCoachAgent(
            ChatModel dashscopeChatModel,
            ChatMemoryManager chatMemoryManager,
            ArtifactShelf artifactShelf) {
        return new CareerCoachAgent(dashscopeChatModel, chatMemoryManager, artifactShelf);
    }

    /**
     * 用户画像整理数据员工 Bean（P2 扩展数据员工）。
     * 将分散画像线索整理为 USER_PROFILE 作用域的结构化画像交付物。
     */
    @Bean
    public ProfileCuratorAgent profileCuratorAgent(
            ChatModel dashscopeChatModel,
            ChatMemoryManager chatMemoryManager,
            ArtifactShelf artifactShelf) {
        return new ProfileCuratorAgent(dashscopeChatModel, chatMemoryManager, artifactShelf);
    }

    /**
     * 晋升路径规划数据员工 Bean（P2 扩展数据员工）。
     * 基于对话历史或上传文档产出晋升路径规划交付物并放入货架。
     */
    @Bean
    public PromotionPlannerAgent promotionPlannerAgent(
            ChatModel dashscopeChatModel,
            ChatMemoryManager chatMemoryManager,
            ArtifactShelf artifactShelf) {
        return new PromotionPlannerAgent(dashscopeChatModel, chatMemoryManager, artifactShelf);
    }

    /**
     * 学习资源推荐员 Bean（P3 数据员工）。
     * 依据用户画像关注领域推荐学习资源，关注领域为空时回退到对话上下文。
     */
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
