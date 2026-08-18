package com.yupi.yuaiagent.history;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Dual-write adapter: new envelope plus optional legacy sink. */
public class ActionHistoryDualWriter {

    private final List<ActionHistoryEvent> events = new CopyOnWriteArrayList<>();
    private final List<String> legacyLines = new CopyOnWriteArrayList<>();

    public void write(ActionHistoryEvent event, String legacyLine) {
        events.add(event);
        if (legacyLine != null) {
            legacyLines.add(legacyLine);
        }
    }

    public List<ActionHistoryEvent> events() {
        return List.copyOf(events);
    }

    public List<String> legacyLines() {
        return List.copyOf(legacyLines);
    }
}
