package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.sessionstate.HandoffScopeContext;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationFingerprintTest {

    @Test
    void cachesStaticPatternsButNotHandoffOrQuota() {
        ActivationFingerprintCache cache = new ActivationFingerprintCache();
        AtomicInteger computes = new AtomicInteger();
        ActivationFingerprintCache.Key key = new ActivationFingerprintCache.Key("fp1", "v1", "resume-agent");
        Set<String> first = cache.getOrCompute(key, () -> {
            computes.incrementAndGet();
            return Set.of("searchKnowledgeBase");
        });
        Set<String> second = cache.getOrCompute(key, () -> {
            computes.incrementAndGet();
            return Set.of("writeFile");
        });
        assertThat(first).isEqualTo(second);
        assertThat(computes.get()).isEqualTo(1);

        cache.evict("resume-agent");
        cache.getOrCompute(key, () -> {
            computes.incrementAndGet();
            return Set.of("searchKnowledgeBase");
        });
        assertThat(computes.get()).isEqualTo(2);
        assertThat(HandoffScopeContext.isActive()).isFalse();
    }
}
