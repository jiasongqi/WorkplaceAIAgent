package com.yupi.yuaiagent.perception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PerceptionCrossValidatorTest {

    private final PerceptionCrossValidator validator = new PerceptionCrossValidator();

    @Test
    void acceptsCloseNumbers() {
        var r = validator.check("1024.55", "1024.50");
        assertThat(r.consistent()).isTrue();
    }

    @Test
    void rejectsDivergentNumbers() {
        var r = validator.check("1024.55", "88");
        assertThat(r.consistent()).isFalse();
        assertThat(r.guidance()).contains("工具观测");
    }
}
