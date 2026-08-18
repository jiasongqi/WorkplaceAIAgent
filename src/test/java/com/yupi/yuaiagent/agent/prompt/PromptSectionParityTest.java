package com.yupi.yuaiagent.agent.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptSectionParityTest {

    @Test
    void offlineRenderIsDeterministicAndSkipsOptionalFailures() {
        PromptSectionContributor profile = new PromptSectionContributor() {
            @Override
            public String id() {
                return "profile";
            }

            @Override
            public String render(PromptContext context) {
                return "profile:" + context.userId();
            }
        };
        PromptSectionContributor optional = new PromptSectionContributor() {
            @Override
            public String id() {
                return "optional";
            }

            @Override
            public String render(PromptContext context) {
                throw new IllegalStateException("boom");
            }
        };
        PromptSectionContributor permission = new PromptSectionContributor() {
            @Override
            public String id() {
                return "permission";
            }

            @Override
            public boolean required() {
                return true;
            }

            @Override
            public String render(PromptContext context) {
                return "perm:allow";
            }
        };
        PromptSectionRenderer renderer = new PromptSectionRenderer(List.of(
                profile,
                optional,
                permission
        ));
        PromptContext ctx = new PromptContext("u1", "c1", "RESUME", Map.of());
        assertThat(renderer.render(ctx)).isEqualTo("profile:u1\nperm:allow");
        assertThat(renderer.order()).containsExactly("profile", "optional", "permission");
    }

    @Test
    void requiredSectionFailureDoesNotDegrade() {
        PromptSectionContributor required = new PromptSectionContributor() {
            @Override
            public String id() {
                return "permission";
            }

            @Override
            public boolean required() {
                return true;
            }

            @Override
            public String render(PromptContext context) {
                throw new IllegalStateException("perm missing");
            }
        };
        PromptSectionRenderer renderer = new PromptSectionRenderer(List.of(required));
        assertThatThrownBy(() -> renderer.render(new PromptContext("u", "c", "RESUME", Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shadowDoesNotDoubleInvokeLoaders() {
        AtomicInteger loads = new AtomicInteger();
        PromptContext prepared = new PromptContext("u", "c", "RESUME", Map.of("n", String.valueOf(loads.incrementAndGet())));
        PromptSectionContributor section = new PromptSectionContributor() {
            @Override
            public String id() {
                return "n";
            }

            @Override
            public String render(PromptContext context) {
                return context.sections().get("n");
            }
        };
        PromptSectionRenderer renderer = new PromptSectionRenderer(List.of(section));
        assertThat(renderer.render(prepared)).isEqualTo("1");
        assertThat(renderer.render(prepared)).isEqualTo("1");
        assertThat(loads.get()).isEqualTo(1);
    }

    private static PromptSectionContributor named(String id, PromptSectionContributor delegate) {
        return new PromptSectionContributor() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String render(PromptContext context) {
                return delegate.render(context);
            }
        };
    }
}
