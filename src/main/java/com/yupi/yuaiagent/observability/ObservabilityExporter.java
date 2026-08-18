package com.yupi.yuaiagent.observability;

public interface ObservabilityExporter {
    default void setup() {
    }

    void record(String eventType, String payload);

    default void flush() {
    }

    default void shutdown() {
    }
}
