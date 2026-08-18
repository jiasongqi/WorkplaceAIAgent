package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.sessionstate.HandoffScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HandoffScopeCacheIsolationTest {

    @AfterEach
    void tearDown() {
        HandoffScopeContext.clear();
    }

    @Test
    void cacheDoesNotStoreHandoffScope() {
        ActivationFingerprintCache cache = new ActivationFingerprintCache();
        HandoffScopeContext.install(List.of("rag.query"));
        cache.getOrCompute(new ActivationFingerprintCache.Key("fp", "1", "escape-agent"),
                () -> Set.of("searchWeb"));
        HandoffScopeContext.clear();
        assertThat(cache.snapshot().values().iterator().next()).containsExactly("searchWeb");
        assertThat(HandoffScopeContext.isActive()).isFalse();
    }
}
