package com.yupi.yuaiagent.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentCodeResolverTest {

    @ParameterizedTest
    @CsvSource({
            "yuManus, yu-manus",
            "yu-manus, yu-manus",
            "manus, yu-manus",
            "resume, resume-agent",
            "ResumeAgent, resume-agent",
            "negotiation, negotiation-agent",
            "escape, escape-agent",
            "consultation, consultation-agent",
            "general, general-agent",
            "digital-employee, digital-employee",
            "admin-agent, admin-agent"
    })
    void mapsRuntimeNamesToPermissionProfiles(String runtimeName, String expectedCode) {
        assertEquals(expectedCode, AgentCodeResolver.resolve(runtimeName));
    }

    @Test
    void blankNameReturnsNull() {
        assertNull(AgentCodeResolver.resolve(null));
        assertNull(AgentCodeResolver.resolve("  "));
    }

    @Test
    void unknownNamePassesThrough() {
        assertEquals("custom-bot", AgentCodeResolver.resolve("custom-bot"));
    }
}
