package com.yupi.yuaiagent.workflow;

/**
 * How the workflow was matched.
 */
public enum MatchType {
    RULE,       // Score-based keyword match
    LLM,        // LLM classification
    FALLBACK    // Generic fallback
}
