package com.yupi.yuaiagent.nlu;

import java.util.List;

/**
 * One-shot NLU context — wraps persistent state + transient alias metadata.
 * Passed to UnifiedNluExtractor as prompt context. NOT persisted.
 *
 * <p>Aliases are input-level metadata, not conversation-level state.
 * "查TX" → alias TX=腾讯资方 is a fact about THIS message, not about the conversation.
 * Next message "查百度" should NOT see entity=腾讯资方 in the state.
 *
 * @param state   current conversation state (persisted)
 * @param aliases alias matches from this message only (transient)
 * @author jsq
 */
public record NluContext(
    ConversationState state,
    List<AliasMatch> aliases
) {
    /**
     * Alias match metadata — extracted from user input, NOT injected into state.
     */
    public record AliasMatch(String alias, String canonical, String entityType) {}

    public boolean hasAliases() {
        return aliases != null && !aliases.isEmpty();
    }

    /**
     * Build the alias hint string for the LLM prompt.
     * Keeps aliases separate from state — LLM sees both but does not confuse them.
     */
    public String aliasHint() {
        if (!hasAliases()) return "none";
        StringBuilder sb = new StringBuilder("Known aliases in this message: ");
        for (int i = 0; i < aliases.size(); i++) {
            AliasMatch a = aliases.get(i);
            if (i > 0) sb.append(", ");
            sb.append(a.alias()).append("=").append(a.canonical());
        }
        return sb.toString();
    }
}
