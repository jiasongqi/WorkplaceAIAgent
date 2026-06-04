package com.yupi.yuaiagent.dto;

import lombok.Data;

/**
 * Data import result summary.
 */
@Data
public class ImportResult {
    private int sessionsImported;
    private int sessionsSkipped;
    private int messagesImported;
    private int favoritesImported;
}
