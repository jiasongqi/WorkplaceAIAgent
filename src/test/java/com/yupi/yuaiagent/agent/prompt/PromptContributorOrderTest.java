package com.yupi.yuaiagent.agent.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PromptContributorOrderTest {

    @Test
    void orderIsStableAndLoadersAreNotReinvoked() {
        AtomicInteger loads = new AtomicInteger();
        PromptContext prepared = new PromptContext("u", "c", "RESUME", Map.of(
                "goal", "g" + loads.incrementAndGet(),
                "profile", "p"));
        PromptSectionRenderer renderer = new PromptSectionRenderer(List.of(
                new MapSectionContributor("goal"),
                new MapSectionContributor("profile")));
        assertThat(renderer.order()).containsExactly("goal", "profile");
        assertThat(renderer.render(prepared)).isEqualTo("g1\np");
        assertThat(renderer.render(prepared)).isEqualTo("g1\np");
        assertThat(loads.get()).isEqualTo(1);
    }
}
