package com.yupi.yuaiagent.workflow.node;

import java.util.List;

/**
 * 并行节点 — 同时执行多个子节点，等待全部完成。
 *
 * @author jsq
 */
public class ParallelNode extends WorkflowNode {

    /** 并行执行的子节点 ID 列表 */
    private List<String> branches;
    /** 是否要求全部分支成功 */
    private boolean requireAll = true;

    public ParallelNode() { super(null, null, "parallel"); }

    public ParallelNode(String id, String name, List<String> branches) {
        super(id, name, "parallel");
        this.branches = branches;
    }

    public List<String> getBranches() { return branches; }
    public void setBranches(List<String> branches) { this.branches = branches; }
    public boolean isRequireAll() { return requireAll; }
    public void setRequireAll(boolean requireAll) { this.requireAll = requireAll; }
}
