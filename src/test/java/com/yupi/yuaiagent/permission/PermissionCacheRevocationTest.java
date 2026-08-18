package com.yupi.yuaiagent.permission;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCacheRevocationTest {

    @Test
    void evictMakesNextComputeSeeNewPatterns() {
        ActivationFingerprintCache cache = new ActivationFingerprintCache();
        ActivationFingerprintCache.Key key = new ActivationFingerprintCache.Key("fp", "1", "resume-agent");
        AtomicInteger computes = new AtomicInteger();
        cache.getOrCompute(key, () -> {
            computes.incrementAndGet();
            return Set.of("searchWeb");
        });
        cache.evict("resume-agent");
        Set<String> after = cache.getOrCompute(key, () -> {
            computes.incrementAndGet();
            return Set.of();
        });
        assertThat(after).isEmpty();
        assertThat(computes.get()).isEqualTo(2);
    }
}
