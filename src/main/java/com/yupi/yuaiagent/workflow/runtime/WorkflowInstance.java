package com.yupi.yuaiagent.workflow.runtime;

import com.yupi.yuaiagent.workflow.node.WorkflowNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流实例 — 一次工作流执行的运行时状态。
 *
 * @author jsq
 */
@Data
@Builder
public class WorkflowInstance {

    /** 实例唯一 ID */
    private String instanceId;

    /** 工作流模板 ID */
    private String workflowId;

    /** 当前执行的节点索引 */
    @Builder.Default
    private int currentNodeIndex = 0;

    /** 工作流状态 */
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.PENDING;

    /** 节点列表 */
    @Builder.Default
    private List<WorkflowNode> nodes = new ArrayList<>();

    /** 上下文变量（节点间传递数据） */
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    /** 执行历史 */
    @Builder.Default
    private List<StepRecord> history = new ArrayList<>();

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;

    /** 触发用户 ID */
    private String userId;

    /** 触发会话 ID */
    private String chatId;

    /**
     * 单步执行记录
     */
    @Data
    @Builder
    public static class StepRecord {
        private String nodeId;
        private String nodeName;
        private WorkflowStatus status;
        private String result;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private long durationMs;
    }
}
