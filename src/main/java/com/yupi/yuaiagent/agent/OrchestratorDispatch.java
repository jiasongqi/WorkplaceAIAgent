package com.yupi.yuaiagent.agent;

import java.util.Locale;
import java.util.Map;

/**
 * Maps routing intents to generic runner codes without invoking an LLM.
 */
public final class OrchestratorDispatch {

    public enum RouteMode {
        OFF,
        SHADOW,
        PRIMARY
    }

    private OrchestratorDispatch() {
    }

    public static String runnerCode(AgentIntent intent) {
        if (intent == null) {
            return "GENERAL";
        }
        return switch (intent) {
            case RESUME -> "RESUME";
            case NEGOTIATION -> "NEGOTIATION";
            case ESCAPE -> "ESCAPE";
            case CONSULTATION -> "CONSULTATION";
            case DATA_QUERY -> "DATA_QUERY";
            case DIGITAL_EMPLOYEE -> "DIGITAL_EMPLOYEE";
            case GENERAL -> "GENERAL";
        };
    }

    public static DispatchSnapshot shadow(AgentIntent intent, AgentRunnerRegistry registry) {
        String expected = runnerCode(intent);
        String actual = registry == null ? null : registry.get(expected).map(AgentRunner::agentCode).orElse(null);
        boolean drift = !expected.equals(actual);
        if (intent == AgentIntent.CONSULTATION) {
            boolean holdsSession = registry != null
                    && registry.get(expected).map(runner -> runner.holdsSession("shadow-chat")).orElse(false);
            return new DispatchSnapshot(expected, actual, drift, holdsSession);
        }
        return new DispatchSnapshot(expected, actual, drift, false);
    }

    public static RouteMode modeFor(AgentIntent intent, Map<String, String> intentModes, RouteMode fallback) {
        if (intentModes == null || intent == null) {
            return fallback == null ? RouteMode.OFF : fallback;
        }
        String raw = intentModes.get(intent.name());
        if (raw == null || raw.isBlank()) {
            return fallback == null ? RouteMode.OFF : fallback;
        }
        try {
            return RouteMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return RouteMode.OFF;
        }
    }

    public record DispatchSnapshot(String expectedRunner, String actualRunner, boolean drift, boolean consultationHoldsSession) {
    }
}
