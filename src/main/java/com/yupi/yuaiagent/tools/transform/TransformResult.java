package com.yupi.yuaiagent.tools.transform;

public record TransformResult(TransformDecision decision, String payload, String reason) {

    public static TransformResult proceed(String payload) {
        return new TransformResult(TransformDecision.PROCEED, payload, null);
    }

    public static TransformResult rewrite(String payload) {
        return new TransformResult(TransformDecision.REWRITE, payload, null);
    }

    public static TransformResult reject(String reason) {
        return new TransformResult(TransformDecision.REJECT, null, reason == null ? "rejected" : reason);
    }
}
