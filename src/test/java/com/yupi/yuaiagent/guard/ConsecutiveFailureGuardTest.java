package com.yupi.yuaiagent.guard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsecutiveFailureGuardTest {

    @Test
    void tripsAfterThreshold() {
        ConsecutiveFailureGuard guard = new ConsecutiveFailureGuard(3);
        guard.recordFailure("a");
        guard.recordFailure("b");
        assertThat(guard.shouldStop()).isFalse();
        guard.recordFailure("c");
        assertThat(guard.shouldStop()).isTrue();
        assertThat(guard.stopMessage()).contains("连续失败");
    }

    @Test
    void successResetsCounter() {
        ConsecutiveFailureGuard guard = new ConsecutiveFailureGuard(2);
        guard.recordFailure("a");
        guard.recordSuccess();
        guard.recordFailure("b");
        assertThat(guard.shouldStop()).isFalse();
        guard.recordFailure("c");
        assertThat(guard.shouldStop()).isTrue();
    }
}
