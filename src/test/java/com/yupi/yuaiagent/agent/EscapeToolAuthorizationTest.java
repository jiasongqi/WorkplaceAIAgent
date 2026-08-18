package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.access.PermissionCheckingToolCallback;
import com.yupi.yuaiagent.access.RuntimeToolFilter;
import com.yupi.yuaiagent.access.RuntimeToolRequestContext;
import com.yupi.yuaiagent.pack.PackPreferenceMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EscapeToolAuthorizationTest {

    @AfterEach
    void tearDown() {
        RuntimeToolRequestContext.clear();
    }

    @Test
    void executionIsFailClosedWhenFilterInstalled() {
        assertThat(RuntimeToolFilter.executionAllowed("generatePDF", Set.of("searchWeb"))).isFalse();
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("generatePDF");
        when(delegate.getToolDefinition()).thenReturn(def);
        RuntimeToolRequestContext.install(new RuntimeToolRequestContext.View(
                Set.of("searchWeb"), PackPreferenceMode.EXPLICIT_PARTIAL, true));
        assertThatThrownBy(() -> new PermissionCheckingToolCallback(delegate).call("{}"))
                .isInstanceOf(SecurityException.class);
    }
}
