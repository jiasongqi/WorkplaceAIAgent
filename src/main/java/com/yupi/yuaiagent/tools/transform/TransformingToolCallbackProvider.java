package com.yupi.yuaiagent.tools.transform;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;

/** Wraps MCP (or any) ToolCallbackProvider so transformers cannot be bypassed. */
public class TransformingToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;
    private final ToolTransformer transformer;

    public TransformingToolCallbackProvider(ToolCallbackProvider delegate, ToolTransformer transformer) {
        this.delegate = delegate;
        this.transformer = transformer;
    }

    @Override
    public org.springframework.ai.tool.ToolCallback[] getToolCallbacks() {
        if (delegate == null) {
            return new org.springframework.ai.tool.ToolCallback[0];
        }
        org.springframework.ai.tool.ToolCallback[] raw = delegate.getToolCallbacks();
        if (raw == null || transformer == null) {
            return raw;
        }
        return Arrays.stream(raw)
                .map(cb -> cb == null ? null : new TransformingToolCallback(cb, transformer))
                .toArray(org.springframework.ai.tool.ToolCallback[]::new);
    }

    public static boolean namesMatch(ToolDefinition definition, String expected) {
        return definition != null && expected != null && expected.equals(definition.name());
    }
}
