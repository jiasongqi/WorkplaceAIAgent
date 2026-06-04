package com.yupi.yuaiagent.agent.data;

import com.yupi.yuaiagent.artifact.model.Artifact;

/**
 * 数据员工加工结果
 * <p>
 * 加工成功时携带产出的 {@link Artifact}；加工失败时携带描述性错误信息。
 *
 * @param success      是否加工成功
 * @param artifact     加工产出的交付物（成功时非空）
 * @param errorMessage 错误信息（失败时非空）
 * @author jsq
 */
public record ProductionResult(
        boolean success,
        Artifact artifact,
        String errorMessage) {

    /**
     * 构造加工成功结果
     *
     * @param artifact 加工产出的交付物
     */
    public static ProductionResult ok(Artifact artifact) {
        return new ProductionResult(true, artifact, null);
    }

    /**
     * 构造加工失败结果
     *
     * @param errorMessage 描述性错误信息
     */
    public static ProductionResult fail(String errorMessage) {
        return new ProductionResult(false, null, errorMessage);
    }
}
