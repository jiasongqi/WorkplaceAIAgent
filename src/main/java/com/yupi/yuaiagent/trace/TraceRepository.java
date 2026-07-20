package com.yupi.yuaiagent.trace;

import com.yupi.yuaiagent.trace.model.ExecutionTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Trace persistence facade — delegates to file or JDBC {@link TraceStore}.
 */
@Repository
@RequiredArgsConstructor
public class TraceRepository {

    private final TraceStore store;

    public ExecutionTrace save(ExecutionTrace trace) {
        return store.save(trace);
    }

    public Optional<ExecutionTrace> findById(String traceId) {
        return store.findById(traceId);
    }

    public List<ExecutionTrace> findByChatId(String chatId) {
        return store.findByChatId(chatId);
    }

    public List<ExecutionTrace> findByChatId(String chatId, int pageNum, int pageSize) {
        return store.findByChatId(chatId, pageNum, pageSize);
    }

    public List<ExecutionTrace> findByUserId(String userId) {
        return store.findByUserId(userId);
    }

    public List<ExecutionTrace> findByUserId(String userId, int pageNum, int pageSize) {
        return store.findByUserId(userId, pageNum, pageSize);
    }

    public void enforceRetentionPolicy(String userId) {
        store.enforceRetentionPolicy(userId);
    }

    public int count() {
        return store.count();
    }
}
