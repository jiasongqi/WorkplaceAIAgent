package com.yupi.yuaiagent.agent.prompt;

import java.util.ArrayList;
import java.util.List;

public class PromptSectionRenderer {

    private final List<PromptSectionContributor> contributors;

    public PromptSectionRenderer(List<PromptSectionContributor> contributors) {
        this.contributors = contributors == null ? List.of() : List.copyOf(contributors);
    }

    public String render(PromptContext context) {
        StringBuilder out = new StringBuilder();
        for (PromptSectionContributor contributor : contributors) {
            try {
                String section = contributor.render(context);
                if (section != null && !section.isBlank()) {
                    if (!out.isEmpty()) {
                        out.append('\n');
                    }
                    out.append(section);
                }
            } catch (RuntimeException ex) {
                if (contributor.required()) {
                    throw ex;
                }
            }
        }
        return out.toString();
    }

    public List<String> order() {
        List<String> ids = new ArrayList<>();
        contributors.forEach(c -> ids.add(c.id()));
        return List.copyOf(ids);
    }
}
