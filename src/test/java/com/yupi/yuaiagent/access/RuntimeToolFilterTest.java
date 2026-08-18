package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.pack.PackPreferenceMode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeToolFilterTest {

    @Test
    void allDisabledExposesOnlyControlTools() {
        ToolCallback terminate = tool("doTerminate");
        ToolCallback search = tool("searchKnowledgeBase");
        ToolCallback[] filtered = RuntimeToolFilter.filter(
                new ToolCallback[]{terminate, search},
                Set.of("searchKnowledgeBase"),
                PackPreferenceMode.EXPLICIT_ALL_DISABLED);
        assertThat(filtered).extracting(cb -> cb.getToolDefinition().name())
                .containsExactly("doTerminate");
    }

    @Test
    void executionIsFailClosed() {
        assertThat(RuntimeToolFilter.executionAllowed("searchKnowledgeBase", Set.of("readFile"))).isFalse();
        assertThat(RuntimeToolFilter.executionAllowed("doTerminate", Set.of())).isTrue();
    }

    private static ToolCallback tool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(callback.getToolDefinition()).thenReturn(def);
        return callback;
    }
}
