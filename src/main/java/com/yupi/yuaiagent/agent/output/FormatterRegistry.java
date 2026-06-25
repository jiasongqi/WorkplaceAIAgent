package com.yupi.yuaiagent.agent.output;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Registry mapping AgentOutput types to their formatters.
 * Falls back to AgentOutput.summary() if no formatter registered.
 *
 * @author jsq
 */
@Component
public class FormatterRegistry {

    private final Map<Class<? extends AgentOutput>, AgentOutputFormatter<?>> formatters;

    public FormatterRegistry() {
        this.formatters = Map.of(
            TextOutput.class, new TextOutputFormatter()
        );
    }

    @SuppressWarnings("unchecked")
    public <T extends AgentOutput> String format(T output) {
        AgentOutputFormatter<T> formatter =
            (AgentOutputFormatter<T>) formatters.get(output.getClass());
        if (formatter != null) {
            return formatter.format(output);
        }
        return output.summary();
    }

    /** TextOutput formatter — just returns the text. */
    private static class TextOutputFormatter implements AgentOutputFormatter<TextOutput> {
        @Override
        public String format(TextOutput output) { return output.text(); }
    }
}
