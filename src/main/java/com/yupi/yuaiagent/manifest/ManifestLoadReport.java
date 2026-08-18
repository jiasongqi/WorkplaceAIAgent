package com.yupi.yuaiagent.manifest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable catalog snapshot and diagnostics produced by {@link ManifestLoader}.
 */
public record ManifestLoadReport<T>(
        Map<String, T> items,
        Map<String, String> sources,
        List<ManifestLoadError> errors,
        String fingerprint
) {
    public ManifestLoadReport {
        items = items == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(items));
        sources = sources == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        errors = errors == null ? List.of() : List.copyOf(errors);
        fingerprint = fingerprint == null ? "" : fingerprint;
    }
}
