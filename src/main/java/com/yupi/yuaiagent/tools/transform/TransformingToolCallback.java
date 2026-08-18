package com.yupi.yuaiagent.tools.transform;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Fail-closed wrapper: rejected or failed transforms never reach the delegate.
 */
public class TransformingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolTransformer transformer;

    public TransformingToolCallback(ToolCallback delegate, ToolTransformer transformer) {
        this.delegate = delegate;
        this.transformer = transformer;
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
        return invoke(functionInput, null);
    }

    @Override
    public String call(String functionInput, org.springframework.ai.chat.model.ToolContext toolContext) {
        return invoke(functionInput, toolContext);
    }

    private String invoke(String functionInput, org.springframework.ai.chat.model.ToolContext toolContext) {
        String name = delegate.getToolDefinition() == null ? "" : delegate.getToolDefinition().name();
        TransformResult result;
        try {
            result = transformer == null
                    ? TransformResult.proceed(functionInput)
                    : transformer.transform(name, functionInput);
        } catch (RuntimeException ex) {
            return "Error: tool transform rejected: transformer failed: " + ex.getMessage();
        }
        if (result == null || result.decision() == TransformDecision.REJECT) {
            return "Error: tool transform rejected: " + (result == null ? "null" : result.reason());
        }
        String payload = result.payload() == null ? functionInput : result.payload();
        if (toolContext == null) {
            return delegate.call(payload);
        }
        return delegate.call(payload, toolContext);
    }
}
