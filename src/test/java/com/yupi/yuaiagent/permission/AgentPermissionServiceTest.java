package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.permission.model.PermissionProfile;
import com.yupi.yuaiagent.sessionstate.HandoffScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPermissionServiceTest {

    private AgentPermissionService service;
    private PermissionProfileRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PermissionProfileRegistry();
        service = new AgentPermissionService();
        ReflectionTestUtils.setField(service, "registry", registry);
        registry.register(PermissionProfile.builder()
                .agentCode("escape-agent")
                .displayName("离职规划")
                .allowedToolPatterns(Set.of("web.search", "file.read", "rag.query", "pdf.generate"))
                .maxToolCallsPerRequest(15)
                .build());
        registry.register(PermissionProfile.builder()
                .agentCode("admin-agent")
                .displayName("admin")
                .admin(true)
                .allowedToolPatterns(Set.of("*"))
                .build());
    }

    @AfterEach
    void tearDown() {
        HandoffScopeContext.clear();
    }

    @Test
    void escapeAgentCanUseRealPdfAndSearchToolsViaAliases() {
        assertTrue(service.checkPermission("escape-agent", "searchWeb"));
        assertTrue(service.checkPermission("escape-agent", "generatePDF"));
        assertTrue(service.checkPermission("escape-agent", "readFile"));
        assertTrue(service.checkPermission("escape-agent", "searchKnowledgeBase"));
    }

    @Test
    void escapeAgentCannotRunTerminal() {
        assertFalse(service.checkPermission("escape-agent", "executeTerminalCommand"));
        assertFalse(service.checkPermission("escape-agent", "writeFile"));
    }

    @Test
    void terminateIsAlwaysAllowedEvenWithoutPattern() {
        assertTrue(service.checkPermission("escape-agent", "doTerminate"));
        assertTrue(service.checkPermission("escape-agent", "checkAsyncToolTask"));
        assertTrue(service.checkPermission("ghost-agent", "doTerminate"));
    }

    @Test
    void handoffScopeDeniesToolsOutsidePacket() {
        HandoffScopeContext.install(List.of("rag.query"));
        assertTrue(service.checkPermission("escape-agent", "searchKnowledgeBase"));
        assertFalse(service.checkPermission("escape-agent", "searchWeb"));
        assertTrue(service.checkPermission("escape-agent", "doTerminate"));
    }

    @Test
    void unknownAgentIsDenied() {
        assertFalse(service.checkPermission("ghost-agent", "searchWeb"));
    }

    @Test
    void adminSkipsPatternChecks() {
        assertTrue(service.checkPermission("admin-agent", "executeTerminalCommand"));
    }
}
