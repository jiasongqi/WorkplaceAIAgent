package com.yupi.yuaiagent.rag;

/**
 * Tunable knobs for {@link RetrievalPipeline} — tool vs chat paths differ on cost/latency.
 */
public record RetrievalOptions(
        String statusFilter,
        int topK,
        boolean multiQuery,
        boolean useHyDE,
        double similarityThreshold
) {
    public static RetrievalOptions toolDefaults() {
        return new RetrievalOptions(null, 3, false, false, 0.5);
    }

    public static RetrievalOptions chatDefaults() {
        return new RetrievalOptions(null, 5, true, false, 0.5);
    }
}
