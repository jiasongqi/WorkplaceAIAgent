package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.permission.model.PermissionProfile;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaCacheIsolationTest {

    @Test
    void quotaIsEvaluatedPerRequestNotFromPatternCache() {
        AgentPermissionService service = new AgentPermissionService();
        PermissionProfileRegistry registry = new PermissionProfileRegistry();
        ReflectionTestUtils.setField(service, "registry", registry);
        registry.register(PermissionProfile.builder()
                .agentCode("resume-agent")
                .maxToolCallsPerRequest(2)
                .build());
        assertTrue(service.isWithinToolCallLimit("resume-agent", 0));
        assertTrue(service.isWithinToolCallLimit("resume-agent", 1));
        org.junit.jupiter.api.Assertions.assertFalse(service.isWithinToolCallLimit("resume-agent", 2));
    }
}
