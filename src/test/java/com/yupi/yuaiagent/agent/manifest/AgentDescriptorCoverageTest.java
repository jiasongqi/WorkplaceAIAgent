package com.yupi.yuaiagent.agent.manifest;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.manifest.ManifestLoadPolicy;
import com.yupi.yuaiagent.manifest.ManifestLoader;
import com.yupi.yuaiagent.registry.AgentDescriptor;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDescriptorCoverageTest {

    @Test
    void classpathAgentsCoverEveryRoutingIntent() {
        var loaded = new ManifestLoader().load(
                "classpath:agents/*.yaml",
                AgentDescriptor.class,
                AgentDescriptor::getAgentCode,
                ManifestLoadPolicy.LENIENT);

        assertThat(loaded.errors()).isEmpty();
        assertThat(loaded.items()).hasSize(AgentIntent.values().length);

        Set<AgentIntent> seen = EnumSet.noneOf(AgentIntent.class);
        for (AgentDescriptor descriptor : loaded.items().values()) {
            AgentManifest manifest = AgentManifestFactory.fromDescriptor(descriptor);
            assertThat(manifest).as(descriptor.getAgentCode()).isNotNull();
            assertThat(seen.add(manifest.intent()))
                    .as("duplicate intent from %s", descriptor.getAgentCode())
                    .isTrue();
            assertThat(descriptor.getIntent()).isEqualTo(manifest.intent().name());
            assertThat(descriptor.getRoutingKeywords()).isNotEmpty();
            assertThat(descriptor.getInputRequirements()).isNotEmpty();
        }
        assertThat(seen).containsExactlyInAnyOrder(AgentIntent.values());
    }
}
