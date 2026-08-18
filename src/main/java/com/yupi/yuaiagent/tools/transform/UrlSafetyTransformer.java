package com.yupi.yuaiagent.tools.transform;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Built-in URL safety policy for the first transformer batch. */
public class UrlSafetyTransformer implements ToolTransformer {

    private static final Set<String> BLOCKED = Set.of("file", "javascript", "data");

    @Override
    public TransformResult transform(String toolName, String input) {
        if (input == null || input.isBlank()) {
            return TransformResult.proceed(input);
        }
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.contains("javascript:") || lower.contains("data:") || lower.contains("file:")) {
            return TransformResult.reject("blocked URL scheme");
        }
        if (!input.contains("://")) {
            return TransformResult.proceed(input);
        }
        try {
            int start = input.indexOf("http");
            if (start < 0) {
                start = input.indexOf("file:");
            }
            if (start < 0) {
                return TransformResult.proceed(input);
            }
            String candidate = input.substring(start).split("[\\s\"']")[0];
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (BLOCKED.contains(scheme)) {
                return TransformResult.reject("blocked URL scheme " + scheme);
            }
            return TransformResult.proceed(input);
        } catch (RuntimeException ex) {
            return TransformResult.reject("invalid URL: " + ex.getMessage());
        }
    }
}
