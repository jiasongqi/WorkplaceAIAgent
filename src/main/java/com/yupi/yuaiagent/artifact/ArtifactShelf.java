package com.yupi.yuaiagent.artifact;

import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactQuery;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;

/**
 * 共享交付物货架（黑板模式核心）。
 * <p>
 * 货架是放货 / 读取 / 查询 / 消费的唯一入口，封装作用域隔离逻辑，并委托
 * {@link ArtifactRepository} 持久化。线程安全由底层仓库的读写锁保证，本类自身无可变状态。
 *
 * @author jsq
 */
@Slf4j
@Component
public class ArtifactShelf {

    @Resource
    private ArtifactRepository artifactRepository;

    /**
     * 放货：保存或更新交付物。
     * <ul>
     *     <li>作用域校验：scope=TASK 必须提供 chatId；scope=USER_PROFILE 必须提供 userId，
     *         否则返回 {@link PutResult#fail(String)}</li>
     *     <li>校验通过则委托 {@link ArtifactRepository#save(Artifact)}：未指定 artifactId 时生成
     *         全局唯一 id，新建时设置 createdAt/updatedAt，再次放货同一 id 时保留原 createdAt 并刷新 updatedAt</li>
     * </ul>
     *
     * @return 放货结果，成功时携带最终的 Artifact（含 artifactId）
     */
    public PutResult put(Artifact artifact) {
        if (artifact == null) {
            return PutResult.fail("交付物不能为空");
        }
        // 作用域校验（Req 6.4 / 6.1 / 6.2）
        if (artifact.getScope() == ArtifactScope.TASK
                && (artifact.getChatId() == null || artifact.getChatId().isBlank())) {
            return PutResult.fail("TASK 作用域交付物必须提供 chatId");
        }
        if (artifact.getScope() == ArtifactScope.USER_PROFILE
                && (artifact.getUserId() == null || artifact.getUserId().isBlank())) {
            return PutResult.fail("USER_PROFILE 作用域交付物必须提供 userId");
        }
        // 仓库内生成 id、设置/刷新时间戳（Req 1.5 / 1.6 / 3.4）
        Artifact saved = artifactRepository.save(artifact);
        log.info("放货成功，artifactId={}，scope={}", saved.getArtifactId(), saved.getScope());
        return PutResult.ok(saved);
    }

    /**
     * 按 artifactId 读取交付物。
     *
     * @return 对应交付物；不存在时返回 {@link Optional#empty()}，绝不抛异常（Req 3.3）
     */
    public Optional<Artifact> get(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) {
            return Optional.empty();
        }
        return artifactRepository.findById(artifactId);
    }

    /**
     * 多条件查询：按 userId / chatId / type / scope / status 做 AND 过滤，
     * 查询条件中为 {@code null} 的字段不参与约束；结果按 createdAt 倒序返回，无匹配返回空列表。
     * <p>
     * 支持按 userId 查询 USER_PROFILE 作用域交付物，返回跨会话累积的全部结果（Req 6.3）。
     */
    public List<Artifact> query(ArtifactQuery condition) {
        if (condition == null) {
            condition = ArtifactQuery.builder().build();
        }
        return artifactRepository.find(condition);
    }

    /**
     * 按发布去重键读取，供幂等发布入口使用。
     */
    public Optional<Artifact> findByDedupKey(String dedupKey) {
        return artifactRepository.findByDedupKey(dedupKey);
    }

    /**
     * 标记消费：将 status 置为 CONSUMED 并刷新 updatedAt（委托
     * {@link ArtifactRepository#updateStatus(String, ArtifactStatus)}）。
     * <p>
     * 幂等：重复调用最终状态一致；id 不存在返回 {@code false} 而非抛异常（Req 5.2 / 5.4）。
     * 标记后查询仍返回该交付物并保留 CONSUMED 状态（Req 5.3，由仓库不删除记录保证）。
     *
     * @return 标记成功返回 true；交付物不存在返回 false
     */
    public boolean markConsumed(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) {
            return false;
        }
        return artifactRepository.updateStatus(artifactId, ArtifactStatus.CONSUMED).isPresent();
    }

    /**
     * 放货结果 DTO。
     *
     * @param success      是否成功
     * @param artifactId   成功时的交付物 id，失败时为 null
     * @param errorMessage 失败时的错误信息，成功时为 null
     * @param artifact     成功时的最终交付物，失败时为 null
     */
    public record PutResult(boolean success, String artifactId, String errorMessage, Artifact artifact) {

        public static PutResult ok(Artifact artifact) {
            return new PutResult(true, artifact.getArtifactId(), null, artifact);
        }

        public static PutResult fail(String errorMessage) {
            return new PutResult(false, null, errorMessage, null);
        }
    }
}
