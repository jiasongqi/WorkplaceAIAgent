package com.yupi.yuaiagent.observability;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ObservabilityExporterBus {

    private final List<ObservabilityExporter> exporters;

    public ObservabilityExporterBus(List<ObservabilityExporter> exporters) {
        this.exporters = exporters == null ? List.of() : List.copyOf(exporters);
        for (ObservabilityExporter exporter : this.exporters) {
            try {
                exporter.setup();
            } catch (RuntimeException ex) {
                log.warn("observability exporter setup failed: {}", ex.getMessage());
            }
        }
    }

    public void record(String eventType, String payload) {
        for (ObservabilityExporter exporter : exporters) {
            try {
                exporter.record(eventType, payload);
            } catch (RuntimeException ex) {
                log.warn("observability exporter record failed: {}", ex.getMessage());
            }
        }
    }
}
