package com.yupi.yuaiagent.agent.prompt;

/**
 * Renders a preloaded section by id. Loaders must run once before this contributor.
 */
public class MapSectionContributor implements PromptSectionContributor {

    private final String id;
    private final boolean required;

    public MapSectionContributor(String id) {
        this(id, false);
    }

    public MapSectionContributor(String id, boolean required) {
        this.id = id;
        this.required = required;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean required() {
        return required;
    }

    @Override
    public String render(PromptContext context) {
        if (context == null || context.sections() == null) {
            if (required) {
                throw new IllegalStateException("missing required prompt section: " + id);
            }
            return "";
        }
        String value = context.sections().get(id);
        if (required && (value == null || value.isBlank())) {
            throw new IllegalStateException("missing required prompt section: " + id);
        }
        return value == null ? "" : value;
    }
}
