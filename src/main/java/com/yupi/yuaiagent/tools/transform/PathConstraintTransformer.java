package com.yupi.yuaiagent.tools.transform;

import java.util.Locale;

/** Blocks path-traversal and local-file URL payloads on file-oriented tools. */
public class PathConstraintTransformer implements ToolTransformer {

    @Override
    public TransformResult transform(String toolName, String input) {
        if (input == null || input.isBlank()) {
            return TransformResult.proceed(input);
        }
        String tool = toolName == null ? "" : toolName.toLowerCase(Locale.ROOT);
        boolean fileTool = tool.contains("file") || tool.contains("terminal") || tool.contains("pdf");
        String lower = input.toLowerCase(Locale.ROOT);
        if (!fileTool) {
            return TransformResult.proceed(input);
        }
        if (lower.contains("..") || lower.contains("%2e%2e")) {
            return TransformResult.reject("path traversal is not allowed");
        }
        return TransformResult.proceed(input);
    }
}
