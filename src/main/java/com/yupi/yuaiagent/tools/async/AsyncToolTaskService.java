package com.yupi.yuaiagent.tools.async;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Submit-Poll async tool tasks for work that may exceed the agent tool timeout (~30s).
 */
@Slf4j
@Service
public class AsyncToolTaskService {

    public enum Status { RUNNING, COMPLETED, FAILED }

    @Data
    @Builder
    public static class Task {
        private String taskId;
        private String toolName;
        private String summary;
        private Status status;
        private String result;
        private String error;
        private Instant createdAt;
        private Instant finishedAt;
    }

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Executor toolExecutor;

    @Value("${app.tools.async-task-ttl-seconds:3600}")
    private long ttlSeconds = 3600;

    public AsyncToolTaskService(@Qualifier("toolExecutor") Executor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    public String submit(String toolName, String summary, Supplier<String> work) {
        purgeExpired();
        String id = UUID.randomUUID().toString();
        Task task = Task.builder()
                .taskId(id)
                .toolName(toolName)
                .summary(summary)
                .status(Status.RUNNING)
                .createdAt(Instant.now())
                .build();
        tasks.put(id, task);
        toolExecutor.execute(() -> {
            try {
                String result = work.get();
                task.setResult(result);
                task.setStatus(Status.COMPLETED);
                task.setFinishedAt(Instant.now());
                log.info("[AsyncTool] completed taskId={} tool={}", id, toolName);
            } catch (Exception e) {
                task.setError(e.getMessage());
                task.setStatus(Status.FAILED);
                task.setFinishedAt(Instant.now());
                log.warn("[AsyncTool] failed taskId={} tool={}: {}", id, toolName, e.getMessage());
            }
        });
        return id;
    }

    public Optional<Task> get(String taskId) {
        purgeExpired();
        return Optional.ofNullable(tasks.get(taskId));
    }

    public String statusMessage(String taskId) {
        Optional<Task> opt = get(taskId);
        if (opt.isEmpty()) {
            return "Async task not found or expired: " + taskId;
        }
        Task t = opt.get();
        return switch (t.getStatus()) {
            case RUNNING -> "Async task " + taskId + " (" + t.getToolName() + ") still RUNNING. "
                    + "Call checkAsyncToolTask again later. Summary: " + t.getSummary();
            case COMPLETED -> "Async task " + taskId + " COMPLETED.\n" + t.getResult();
            case FAILED -> "Async task " + taskId + " FAILED: " + t.getError();
        };
    }

    public static String submittedMessage(String taskId, String toolName) {
        return "Async task submitted. taskId=" + taskId + " tool=" + toolName
                + ". Waiting for completion — call checkAsyncToolTask(taskId=\"" + taskId + "\") on a later turn.";
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minusSeconds(Math.max(60, ttlSeconds));
        tasks.entrySet().removeIf(e -> {
            Task t = e.getValue();
            Instant ref = t.getFinishedAt() != null ? t.getFinishedAt() : t.getCreatedAt();
            return ref != null && ref.isBefore(cutoff);
        });
    }
}
