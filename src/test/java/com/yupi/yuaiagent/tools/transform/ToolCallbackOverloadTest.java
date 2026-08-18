package com.yupi.yuaiagent.tools.transform;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallbackOverloadTest {

    @Test
    void singleArgCallCannotBypassReject() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("searchWeb");
        when(delegate.getToolDefinition()).thenReturn(def);
        new TransformingToolCallback(delegate, new UrlSafetyTransformer()).call("file://secret");
        verify(delegate, never()).call(anyString());
    }
}
