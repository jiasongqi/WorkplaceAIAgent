package com.yupi.yuaiagent.manifest;

/**
 * One resource-level manifest loading failure.
 */
public record ManifestLoadError(
        String resource,
        String manifestType,
        String message,
        String recommendation
) {
}
