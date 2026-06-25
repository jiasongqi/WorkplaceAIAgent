package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.output.FormatterRegistry;
import com.yupi.yuaiagent.agent.task.ExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates all Agent execution results into a final answer.
 * Uses FormatterRegistry for structured output, then LLM for synthesis.
 *
 * @author jsq
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResultAggregator {

    private final FormatterRegistry formatterRegistry;

    /**
     * Aggregate results into a single formatted response.
     * Phase 1: simple concatenation. Phase 2: LLM synthesis.
     */
    public Flux<String> aggregate(String question, List<ExecutionResult> results) {
        // Format each successful result
        String formatted = results.stream()
            .filter(ExecutionResult::isSuccess)
            .map(r -> formatterRegistry.format(r.output()))
            .collect(Collectors.joining("\n\n"));

        // Collect skipped agents
        String skipped = results.stream()
            .filter(ExecutionResult::isSkipped)
            .map(r -> r.agentId() + "（已跳过）")
            .collect(Collectors.joining("、"));

        StringBuilder sb = new StringBuilder(formatted);
        if (!skipped.isEmpty()) {
            sb.append("\n\n（以下专家未参与：").append(skipped).append("）");
        }

        return Flux.just(sb.toString());
    }
}
