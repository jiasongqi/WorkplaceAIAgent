package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.access.RuntimeToolFilter;
import com.yupi.yuaiagent.access.RuntimeToolRequestContext;
import com.yupi.yuaiagent.pack.PackPreferenceMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NegotiationToolAuthorizationTest {

    @AfterEach
    void tearDown() {
        RuntimeToolRequestContext.clear();
    }

    @Test
    void requestFilterHidesDeniedTools() {
        ToolCallback search = tool("searchWeb");
        ToolCallback terminate = tool("doTerminate");
        RuntimeToolRequestContext.install(new RuntimeToolRequestContext.View(
                Set.of("doTerminate"), PackPreferenceMode.EXPLICIT_ALL_DISABLED, true));
        ToolCallback[] filtered = RuntimeToolFilter.filter(
                new ToolCallback[]{search, terminate}, Set.of("doTerminate"), PackPreferenceMode.EXPLICIT_ALL_DISABLED);
        assertThat(filtered).extracting(cb -> cb.getToolDefinition().name()).containsExactly("doTerminate");
    }

    private static ToolCallback tool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(callback.getToolDefinition()).thenReturn(def);
        return callback;
    }
}
