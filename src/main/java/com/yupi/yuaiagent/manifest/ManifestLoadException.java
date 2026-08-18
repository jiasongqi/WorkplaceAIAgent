package com.yupi.yuaiagent.manifest;

import java.util.List;

public class ManifestLoadException extends RuntimeException {

    private final List<ManifestLoadError> errors;

    public ManifestLoadException(String pattern, List<ManifestLoadError> errors) {
        super("Failed to load manifests from " + pattern + ": " + summarize(errors));
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<ManifestLoadError> getErrors() {
        return errors;
    }

    private static String summarize(List<ManifestLoadError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "unknown error";
        }
        return errors.getFirst().resource() + " - " + errors.getFirst().message();
    }
}
