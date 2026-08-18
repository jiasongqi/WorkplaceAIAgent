package com.yupi.yuaiagent.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Indexes {@link AgentRunner} beans by {@link AgentRunner#agentCode()}.
 * Duplicate codes fail startup.
 */
public class AgentRunnerRegistry {

    private final Map<String, AgentRunner> runners;

    public AgentRunnerRegistry(List<AgentRunner> runnerList) {
        Map<String, AgentRunner> indexed = new LinkedHashMap<>();
        if (runnerList != null) {
            for (AgentRunner runner : runnerList) {
                if (runner == null || runner.agentCode() == null || runner.agentCode().isBlank()) {
                    throw new IllegalStateException("AgentRunner is missing agentCode()");
                }
                AgentRunner previous = indexed.put(runner.agentCode(), runner);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate AgentRunner agentCode=" + runner.agentCode());
                }
            }
        }
        this.runners = Map.copyOf(indexed);
    }

    public Optional<AgentRunner> get(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(runners.get(agentCode));
    }

    public Map<String, AgentRunner> asMap() {
        return runners;
    }

    public int size() {
        return runners.size();
    }
}
