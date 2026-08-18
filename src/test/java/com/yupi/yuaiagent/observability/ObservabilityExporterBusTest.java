package com.yupi.yuaiagent.observability;

import com.yupi.yuaiagent.history.ActionHistoryDualWriter;
import com.yupi.yuaiagent.history.ActionHistoryEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityExporterBusTest {

    @Test
    void oneExporterFailureDoesNotBlockOthers() {
        AtomicInteger ok = new AtomicInteger();
        ObservabilityExporter bad = (type, payload) -> {
            throw new IllegalStateException("down");
        };
        ObservabilityExporter good = (type, payload) -> ok.incrementAndGet();
        ObservabilityExporterBus bus = new ObservabilityExporterBus(java.util.List.of(bad, good));
        bus.record("tool", "{}");
        assertThat(ok.get()).isEqualTo(1);
    }

    @Test
    void actionHistoryDualWritesEnvelopeAndLegacy() {
        ActionHistoryDualWriter writer = new ActionHistoryDualWriter();
        writer.write(new ActionHistoryEvent("e1", Instant.parse("2026-08-17T00:00:00Z"), "sse", "token", "c1", Map.of("n", 1)),
                "legacy-token");
        assertThat(writer.events()).hasSize(1);
        assertThat(writer.legacyLines()).containsExactly("legacy-token");
    }
}
