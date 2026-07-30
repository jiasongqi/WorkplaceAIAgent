package com.yupi.yuaiagent.agent.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.artifact.ArtifactPublishPolicy;
import com.yupi.yuaiagent.artifact.ArtifactPublisher;
import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.ArtifactTypeCatalog;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.trace.TraceContext;
import com.yupi.yuaiagent.trace.TraceContextHolder;

/**
 * 数据员工 Agent 抽象基类。
 * <p>
 * 定义数据员工"加工 → 封装 Artifact → 放货"的统一模板：
 * <ul>
 *     <li>{@link #producerName()}：子类返回该数据员工的标识名称，统一写入 {@link Artifact#getProducer()}（Req 7.3）</li>
 *     <li>{@link #doProduce(ProductionContext)}：子类实现具体加工逻辑，仅产出 Artifact 主体内容（type/title/content 等），不负责放货</li>
 *     <li>{@link #produce(ProductionContext)}：模板方法（final），统一设置 producer/scope/status 并通过货架放货</li>
 * </ul>
 * 子类（如数据分析师 DataAnalystAgent）只需关注"如何加工"，无需关心放货流程与字段约定。
 *
 * @author jsq
 */
public abstract class DataEmployeeAgent {

    /**
     * 共享交付物货架（黑板），由子类构造时透传注入。
     */
    protected final ArtifactPublisher artifactPublisher;

    protected DataEmployeeAgent(ArtifactShelf artifactShelf) {
        this(new ArtifactPublisher(artifactShelf,
                new ArtifactPublishPolicy(ArtifactTypeCatalog.defaults(), new ObjectMapper())));
    }

    protected DataEmployeeAgent(ArtifactPublisher artifactPublisher) {
        this.artifactPublisher = artifactPublisher;
    }

    /**
     * 数据员工标识名称（如 "数据分析师"），用于写入 {@link Artifact#getProducer()}。
     *
     * @return 非空的数据员工标识名
     */
    public abstract String producerName();

    /**
     * 子类实现的数据加工逻辑：产出 Artifact 主体内容（type/title/content 等），不负责放货。
     * <p>
     * 加工成功返回 {@link ProductionResult#ok(Artifact)}；
     * 加工失败（如输入为空、无法获取数据）返回 {@link ProductionResult#fail(String)} 并携带描述性错误信息。
     *
     * @param context 数据员工执行上下文
     * @return 加工结果（成功时携带 Artifact，失败时携带错误信息）
     */
    protected abstract ProductionResult doProduce(ProductionContext context);

    /**
     * 统一执行入口（模板方法）：加工 → 组装/确认 Artifact（producer/scope/status）→ 放货。
     * <ul>
     *     <li>加工失败（{@link ProductionResult#success()} 为 false）：不放货，直接返回失败结果（Req 8.6）</li>
     *     <li>加工成功：统一设置 producer={@link #producerName()}（Req 7.3）、status=READY（Req 8.5）、
     *         scope 默认 TASK（会话内任务交付物，Req 7.4），并确认 userId/chatId，最后通过
     *         {@link ArtifactShelf#put(Artifact)} 放货（Req 7.2）</li>
     * </ul>
     *
     * @param context 数据员工执行上下文
     * @return 放货结果；加工失败时返回 {@link ArtifactShelf.PutResult#fail(String)}
     */
    public final ArtifactShelf.PutResult produce(ProductionContext context) {
        ProductionResult result = doProduce(context);
        // 加工失败：不放货，直接返回（Req 8.6）
        if (result == null || !result.success()) {
            String message = (result == null) ? "数据加工结果为空" : result.errorMessage();
            return ArtifactShelf.PutResult.fail(message);
        }
        Artifact artifact = result.artifact();
        if (artifact == null) {
            return ArtifactShelf.PutResult.fail("数据加工标记成功但未产出交付物");
        }
        // 统一设置/确认放货字段，子类只需产出主体内容
        artifact.setProducer(producerName());                       // Req 7.3：producer 恒为数据员工标识名
        if (artifact.getScope() == null) {
            artifact.setScope(ArtifactScope.TASK);                  // Req 7.4：默认会话内任务交付物
        }
        // 确认归属键：子类未设置时以上下文补全，保证作用域校验可通过
        if (context != null) {
            if (artifact.getUserId() == null || artifact.getUserId().isBlank()) {
                artifact.setUserId(context.userId());
            }
            if (artifact.getChatId() == null || artifact.getChatId().isBlank()) {
                artifact.setChatId(context.chatId());
            }
        }
        TraceContext traceContext = TraceContextHolder.get();
        String traceId = traceContext != null && traceContext.getTrace() != null
                ? traceContext.getTrace().getTraceId() : null;
        return artifactPublisher.publish(artifact, traceId);
    }
}
