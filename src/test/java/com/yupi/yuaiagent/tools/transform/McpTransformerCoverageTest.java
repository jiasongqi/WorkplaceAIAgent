package com.yupi.yuaiagent.tools.transform;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpTransformerCoverageTest {

    @Test
    void providerWrapRejectsUnsafeUrlWithoutDelegateCall() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("searchWeb");
        when(delegate.getToolDefinition()).thenReturn(def);
        TransformingToolCallbackProvider provider = new TransformingToolCallbackProvider(
                new StaticToolCallbackProvider(delegate), new UrlSafetyTransformer());
        ToolCallback[] wrapped = provider.getToolCallbacks();
        assertThat(wrapped).hasSize(1);
        wrapped[0].call("javascript:alert(1)");
        verify(delegate, never()).call(anyString());
    }
}
