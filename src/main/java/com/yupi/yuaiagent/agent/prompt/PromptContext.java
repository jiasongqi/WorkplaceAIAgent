package com.yupi.yuaiagent.agent.prompt;

import java.util.Map;

public record PromptContext(String userId, String chatId, String taskType, Map<String, String> sections) {
    public PromptContext {
        sections = sections == null ? Map.of() : Map.copyOf(sections);
    }
}
