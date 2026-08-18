package com.yupi.yuaiagent.agent.manifest;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.manifest.ManifestLoadPolicy;
import com.yupi.yuaiagent.manifest.ManifestLoader;
import com.yupi.yuaiagent.registry.AgentDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentManifestParityTest {

    @Test
    void yamlDerivedStaticFieldsMatchHardcodedManifests() {
        var loaded = new ManifestLoader().load(
                "classpath:agents/*.yaml",
                AgentDescriptor.class,
                AgentDescriptor::getAgentCode,
                ManifestLoadPolicy.LENIENT);
        Map<AgentIntent, AgentManifest> fromYaml = AgentManifestFactory.fromDescriptors(loaded.items().values());
        Map<AgentIntent, AgentManifest> legacy = AgentManifestFactory.legacyManifests();

        for (AgentIntent intent : AgentIntent.values()) {
            assertThat(fromYaml.get(intent)).as(intent.name()).isNotNull();
            assertThat(AgentManifestFactory.staticView(fromYaml.get(intent)))
                    .as(intent.name())
                    .isEqualTo(AgentManifestFactory.staticView(legacy.get(intent)));
        }
    }

    @Test
    void shadowKeepsHardcodedRoutingWhenYamlKeywordsDiffer() {
        AgentDescriptor misleading = AgentDescriptor.builder()
                .agentCode("resume-agent")
                .intent("RESUME")
                .displayName("简历优化专家")
                .description("擅长简历优化、求职、面试技巧、offer 选择。输入需要经历/目标岗位描述。")
                .routingKeywords(List.of("无关词"))
                .inputRequirements(List.of("text"))
                .enabled(true)
                .build();

        AgentManifestRegistry shadow = new AgentManifestRegistry(
                AgentMetadataProperties.Source.SHADOW, List.of(misleading));
        AgentManifestRegistry registry = new AgentManifestRegistry(
                AgentMetadataProperties.Source.REGISTRY, List.of(misleading));

        assertThat(shadow.suggest("帮我优化一下简历突出项目", 0.9)).isEqualTo(AgentIntent.RESUME);
        assertThat(registry.suggest("帮我优化一下简历突出项目", 0.9)).isNull();
    }
}
