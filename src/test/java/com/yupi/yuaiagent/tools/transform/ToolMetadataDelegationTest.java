package com.yupi.yuaiagent.tools.transform;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolMetadataDelegationTest {

    @Test
    void metadataAndDefinitionAreDelegated() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        ToolMetadata metadata = mock(ToolMetadata.class);
        when(def.name()).thenReturn("searchWeb");
        when(delegate.getToolDefinition()).thenReturn(def);
        when(delegate.getToolMetadata()).thenReturn(metadata);
        TransformingToolCallback wrapped = new TransformingToolCallback(delegate, new UrlSafetyTransformer());
        assertThat(wrapped.getToolDefinition()).isSameAs(def);
        assertThat(wrapped.getToolMetadata()).isSameAs(metadata);
    }
}
