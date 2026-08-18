package com.yupi.yuaiagent.agent.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PromptShadowSideEffectTest {

    @Test
    void shadowRenderDoesNotReloadSections() {
        AtomicInteger offered = new AtomicInteger();
        PromptContext prepared = new PromptContext("u", "c", "RESUME", Map.of("artifact", "id-1"));
        offered.incrementAndGet();
        PromptSectionRenderer renderer = new PromptSectionRenderer(List.of(new MapSectionContributor("artifact")));
        renderer.render(prepared);
        renderer.render(prepared);
        assertThat(offered.get()).isEqualTo(1);
    }
}
