package com.yupi.yuaiagent.artifact.recall;

import java.util.List;

/**
 * Side-effect-free artifact recall result.
 */
public record RecallResult(String injectionText, List<String> offeredArtifactIds) {

    public static RecallResult empty() {
        return new RecallResult("", List.of());
    }
}
