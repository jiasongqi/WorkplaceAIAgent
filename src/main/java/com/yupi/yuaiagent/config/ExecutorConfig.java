package com.yupi.yuaiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool configuration for Agent Runtime.
 * <p>
 * Replaces default ForkJoinPool.commonPool() with purpose-built executors
 * to prevent thread starvation under high concurrency.
 *
 * <ul>
 *   <li>{@code agentExecutor} — Agent execution (IO-bound, LLM calls 5-30s)</li>
 *   <li>{@code toolExecutor} — Tool/MCP calls (IO-bound, may block 5-60s)</li>
 *   <li>{@code profileExecutor} — Async profile updates (low priority, non-critical)</li>
 * </ul>
 *
 * @author jsq
 */
@Configuration
public class ExecutorConfig {

    /**
     * Primary executor for Agent execution (Orchestrator, sub-agents).
     * IO-bound: threads spend most time waiting for LLM responses.
     */
    @Bean("agentExecutor")
    public Executor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("agent-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Executor for Tool/MCP calls.
     * IO-bound: external service calls may be slow.
     * Separate from agentExecutor to prevent tool blocking from starving agent threads.
     */
    @Bean("toolExecutor")
    public Executor toolExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix("tool-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Low-priority executor for async profile updates.
     * Profile update is non-critical — failure should never block user response.
     */
    @Bean("profileExecutor")
    public Executor profileExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("profile-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    /**
     * Low-priority executor for memory extraction pipeline.
     * Extraction is non-critical and async — uses CallerRunsPolicy to avoid silent data loss.
     * If the queue is full, the caller thread runs the task (slower but no silent drops).
     */
    @Bean("memoryExtractionExecutor")
    public Executor memoryExtractionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("mem-extract-");
        // CallerRunsPolicy: 队列满时由调用线程执行，比 DiscardPolicy 更安全（不会静默丢失提取任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated executor for MemoryCoordinator parallel layer queries.
     * Replaces ForkJoinPool.commonPool() to prevent thread starvation.
     * Memory queries are IO-bound (file reads + vector search), short-lived (2s timeout).
     */
    @Bean("memoryQueryExecutor")
    public Executor memoryQueryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(128);
        executor.setThreadNamePrefix("mem-query-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
