package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.ExecutionTrace;

import java.util.List;
import java.util.Optional;

/**
 * Pluggable trace persistence ({@code app.storage.type=file|jdbc}).
 */
public interface TraceStore {

    ExecutionTrace save(ExecutionTrace trace);

    Optional<ExecutionTrace> findById(String traceId);

    List<ExecutionTrace> findByChatId(String chatId);

    List<ExecutionTrace> findByChatId(String chatId, int pageNum, int pageSize);

    List<ExecutionTrace> findByUserId(String userId);

    List<ExecutionTrace> findByUserId(String userId, int pageNum, int pageSize);

    void enforceRetentionPolicy(String userId);

    int count();
}
