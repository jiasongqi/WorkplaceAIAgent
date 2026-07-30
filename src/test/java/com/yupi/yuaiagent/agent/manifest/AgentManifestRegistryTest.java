package com.yupi.yuaiagent.agent.manifest;

import com.yupi.yuaiagent.agent.AgentIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentManifestRegistryTest {

    private final AgentManifestRegistry registry = new AgentManifestRegistry();

    @Test
    void ranksResumeForResumeKeywords() {
        AgentIntent suggested = registry.suggest("帮我优化一下简历突出项目", 0.9);
        assertThat(suggested).isEqualTo(AgentIntent.RESUME);
    }

    @Test
    void penalizeLowersRank() {
        registry.penalize(AgentIntent.RESUME, 0.3);
        var ranked = registry.rank("简历 求职");
        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).manifest().intent()).isEqualTo(AgentIntent.RESUME);
        assertThat(ranked.get(0).score()).isEqualTo(2.0 * 0.3); // 2 keyword hits × 0.3 boost
    }
}
