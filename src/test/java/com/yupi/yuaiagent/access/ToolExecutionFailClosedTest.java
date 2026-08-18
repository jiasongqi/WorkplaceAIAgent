package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.pack.PackPreferenceMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolExecutionFailClosedTest {

    @AfterEach
    void tearDown() {
        RuntimeToolRequestContext.clear();
    }

    @Test
    void deniedToolNeverReachesDelegate() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("writeFile");
        when(delegate.getToolDefinition()).thenReturn(def);
        RuntimeToolRequestContext.install(new RuntimeToolRequestContext.View(
                Set.of("readFile"), PackPreferenceMode.EXPLICIT_PARTIAL, true));
        assertThatThrownBy(() -> new PermissionCheckingToolCallback(delegate).call("{}"))
                .isInstanceOf(SecurityException.class);
        verify(delegate, never()).call(anyString());
    }
}
