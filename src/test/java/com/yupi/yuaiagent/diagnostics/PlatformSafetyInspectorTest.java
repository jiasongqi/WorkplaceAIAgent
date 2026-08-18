package com.yupi.yuaiagent.diagnostics;

import com.yupi.yuaiagent.permission.model.PermissionProfile;
import com.yupi.yuaiagent.registry.AgentDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSafetyInspectorTest {

    private final PlatformSafetyInspector inspector = new PlatformSafetyInspector();

    @Test
    void reportsPermissionPatternsThatMatchNoRegisteredTool() {
        PermissionProfile consultation = PermissionProfile.builder()
                .agentCode("consultation-agent")
                .allowedToolPatterns(Set.of("calendar.*", "rag.query"))
                .build();

        PlatformSafetyReport report = inspector.inspect(
                Set.of("searchKnowledgeBase", "searchWeb"),
                List.of(consultation),
                List.of()
        );

        assertEquals(Set.of("calendar.*"),
                report.unmatchedPermissionPatterns().get("consultation-agent"));
        assertFalse(report.unmatchedPermissionPatterns()
                .get("consultation-agent").contains("rag.query"));
    }

    @Test
    void reportsAgentsWhosePermissionProfileIsMissing() {
        AgentDescriptor agent = AgentDescriptor.builder()
                .agentCode("resume-agent")
                .permissionProfile("missing-profile")
                .build();

        PlatformSafetyReport report = inspector.inspect(
                Set.of("searchKnowledgeBase"),
                List.of(PermissionProfile.builder().agentCode("resume-agent").build()),
                List.of(agent)
        );

        assertEquals(Set.of("resume-agent -> missing-profile"),
                report.agentsWithMissingPermissionProfiles());
        assertTrue(report.hasWarnings());
    }

    @Test
    void healthyCatalogProducesNoWarnings() {
        PermissionProfile profile = PermissionProfile.builder()
                .agentCode("resume-agent")
                // Legacy alias intentionally proves diagnostics use the current authorization matcher.
                .allowedToolPatterns(Set.of("rag.query"))
                .build();
        AgentDescriptor agent = AgentDescriptor.builder()
                .agentCode("resume-agent")
                .permissionProfile("resume-agent")
                .build();

        PlatformSafetyReport report = inspector.inspect(
                Set.of("searchKnowledgeBase"),
                List.of(profile),
                List.of(agent)
        );

        assertTrue(report.unmatchedPermissionPatterns().isEmpty());
        assertTrue(report.agentsWithMissingPermissionProfiles().isEmpty());
        assertFalse(report.hasWarnings());
    }

    @Test
    void nullCatalogsProduceAnEmptyReport() {
        PlatformSafetyReport report = inspector.inspect(null, null, null);

        assertTrue(report.unmatchedPermissionPatterns().isEmpty());
        assertTrue(report.agentsWithMissingPermissionProfiles().isEmpty());
        assertFalse(report.hasWarnings());
    }
}
