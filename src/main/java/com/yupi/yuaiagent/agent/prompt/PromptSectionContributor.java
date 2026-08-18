package com.yupi.yuaiagent.agent.prompt;

public interface PromptSectionContributor {
    String id();

    default boolean required() {
        return false;
    }

    String render(PromptContext context);
}
