package com.yupi.yuaiagent.access;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Execution-time fail-closed check so LLM-visible lists cannot bypass authorization.
 */
public class PermissionCheckingToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public PermissionCheckingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String functionInput) {
        String name = delegate.getToolDefinition() == null ? "" : delegate.getToolDefinition().name();
        ViewGate.rejectIfDenied(name);
        return delegate.call(functionInput);
    }

    static final class ViewGate {
        private ViewGate() {
        }

        static void rejectIfDenied(String toolName) {
            RuntimeToolRequestContext.View view = RuntimeToolRequestContext.current().orElse(null);
            if (view == null || !view.filterEnabled()) {
                return;
            }
            if (!RuntimeToolFilter.executionAllowed(toolName, view.effectivePatterns())) {
                throw new SecurityException("tool execution denied: " + toolName);
            }
        }
    }
}
