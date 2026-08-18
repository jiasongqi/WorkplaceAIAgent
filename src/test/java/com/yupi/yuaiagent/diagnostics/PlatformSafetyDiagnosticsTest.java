package com.yupi.yuaiagent.diagnostics;

import com.yupi.yuaiagent.permission.PermissionProfileRegistry;
import com.yupi.yuaiagent.registry.AgentRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformSafetyDiagnosticsTest {

    @Test
    void inspectionFailureDoesNotAbortApplicationStartup() {
        ToolCallback callback = tool("searchWeb");
        AgentRegistry agentRegistry = mock(AgentRegistry.class);
        PermissionProfileRegistry permissionRegistry = mock(PermissionProfileRegistry.class);
        PlatformSafetyInspector inspector = mock(PlatformSafetyInspector.class);
        when(permissionRegistry.getAll()).thenReturn(List.of());
        when(agentRegistry.list()).thenReturn(List.of());
        when(inspector.inspect(any(), any(), any()))
                .thenThrow(new IllegalStateException("diagnostic failure"));

        PlatformSafetyDiagnostics diagnostics = new PlatformSafetyDiagnostics(
                new ToolCallback[]{callback}, agentRegistry, permissionRegistry, inspector);

        assertDoesNotThrow(() -> diagnostics.run(null));
    }

    @Test
    void constructorDefensivelyCopiesInjectedToolArray() {
        ToolCallback original = tool("searchWeb");
        ToolCallback replacement = tool("executeTerminalCommand");
        ToolCallback[] injected = {original};
        AgentRegistry agentRegistry = mock(AgentRegistry.class);
        PermissionProfileRegistry permissionRegistry = mock(PermissionProfileRegistry.class);
        PlatformSafetyInspector inspector = mock(PlatformSafetyInspector.class);
        when(permissionRegistry.getAll()).thenReturn(List.of());
        when(agentRegistry.list()).thenReturn(List.of());
        when(inspector.inspect(any(), any(), any()))
                .thenReturn(new PlatformSafetyReport(Map.of(), Set.of()));

        PlatformSafetyDiagnostics diagnostics = new PlatformSafetyDiagnostics(
                injected, agentRegistry, permissionRegistry, inspector);
        injected[0] = replacement;

        diagnostics.run(null);

        verify(inspector).inspect(eq(Set.of("searchWeb")), any(), any());
    }

    private static ToolCallback tool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        org.springframework.ai.tool.definition.ToolDefinition definition =
                mock(org.springframework.ai.tool.definition.ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        when(callback.getToolDefinition()).thenReturn(definition);
        return callback;
    }
}
