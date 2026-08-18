package com.yupi.yuaiagent.manifest;

import com.yupi.yuaiagent.pack.ExpertPackDefinition;
import com.yupi.yuaiagent.permission.model.PermissionProfile;
import com.yupi.yuaiagent.registry.AgentDescriptor;
import com.yupi.yuaiagent.skill.SkillDefinition;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestLoaderClasspathParityTest {

    private final ManifestLoader loader = new ManifestLoader();

    @Test
    void unifiedLoaderSeesCurrentClasspathCatalogs() {
        var skills = loader.load("classpath:skills/*.yaml", SkillDefinition.class,
                SkillDefinition::getName, ManifestLoadPolicy.LENIENT);
        var agents = loader.load("classpath:agents/*.yaml", AgentDescriptor.class,
                AgentDescriptor::getAgentCode, ManifestLoadPolicy.LENIENT);
        var packs = loader.load("classpath:packs/*.yaml", ExpertPackDefinition.class,
                ExpertPackDefinition::getPackId, ManifestLoadPolicy.LENIENT);
        var permissions = loader.load("classpath:permissions/*.yaml", PermissionProfile.class,
                PermissionProfile::getAgentCode, ManifestLoadPolicy.LENIENT);

        assertEquals(4, skills.items().size());
        assertEquals(7, agents.items().size());
        assertEquals(3, packs.items().size());
        assertEquals(9, permissions.items().size());
        assertEquals(Set.of(
                "resume-agent",
                "negotiation-agent",
                "escape-agent",
                "general-agent",
                "consultation-agent",
                "data-agent",
                "digital-employee"),
                agents.items().keySet());
        assertTrue(skills.errors().isEmpty());
        assertTrue(agents.errors().isEmpty());
        assertTrue(packs.errors().isEmpty());
        assertTrue(permissions.errors().isEmpty());
    }
}
