package com.yupi.yuaiagent.agent.loop;

import java.util.Collections;
import java.util.List;

/**
 * Structured terminal payload for an Agent Loop (Ch4 §2.4).
 */
public record AgentLoopResult(
        Status status,
        String summary,
        List<ArtifactRef> artifacts,
        String reasoningTrace,
        boolean needsHumanHelp,
        List<String> incompleteItems
) {
    public enum Status {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED
    }

    public record ArtifactRef(String type, String pathOrId) {
    }

    public static AgentLoopResult success(String summary, String reasoningTrace) {
        return new AgentLoopResult(
                Status.SUCCESS,
                summary,
                List.of(),
                reasoningTrace,
                false,
                List.of());
    }

    public static AgentLoopResult partial(String summary, List<String> incomplete,
                                          String reasoningTrace, boolean needsHumanHelp) {
        return new AgentLoopResult(
                Status.PARTIAL_SUCCESS,
                summary,
                List.of(),
                reasoningTrace,
                needsHumanHelp,
                incomplete == null ? List.of() : List.copyOf(incomplete));
    }

    public static AgentLoopResult failed(String summary, String reasoningTrace, boolean needsHumanHelp) {
        return new AgentLoopResult(
                Status.FAILED,
                summary,
                List.of(),
                reasoningTrace,
                needsHumanHelp,
                List.of());
    }

    public AgentLoopResult withArtifacts(List<ArtifactRef> refs) {
        return new AgentLoopResult(status, summary,
                refs == null ? List.of() : List.copyOf(refs),
                reasoningTrace, needsHumanHelp, incompleteItems);
    }

    /** User-facing wrap-up text when budget is exhausted. */
    public String toUserFacingWrapUp() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 收尾（预算/步数已用尽 · ").append(status.name()).append("）\n\n");
        if (summary != null && !summary.isBlank()) {
            sb.append(summary).append("\n\n");
        }
        if (incompleteItems != null && !incompleteItems.isEmpty()) {
            sb.append("**未完成项**：\n");
            for (String item : incompleteItems) {
                sb.append("- ").append(item).append("\n");
            }
            sb.append("\n");
        }
        sb.append("> 置信度声明：以上基于已执行步骤的部分结果；如需继续请缩小范围或开启人工协助。\n");
        if (needsHumanHelp) {
            sb.append("> needs_human_help=true\n");
        }
        return sb.toString();
    }

    public static AgentLoopResult empty() {
        return new AgentLoopResult(Status.FAILED, "", Collections.emptyList(), "", false, List.of());
    }
}
