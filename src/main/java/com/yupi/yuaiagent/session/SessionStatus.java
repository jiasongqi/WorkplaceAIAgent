package com.yupi.yuaiagent.session;

/**
 * Session lifecycle status — three-state model.
 * <p>
 * Transitions:
 * <pre>
 *   ACTIVE  → ARCHIVED  (user archives)
 *   ACTIVE  → DELETED   (user deletes — soft delete)
 *   ARCHIVED → ACTIVE   (user unarchives)
 *   ARCHIVED → DELETED  (user deletes — soft delete)
 *   DELETED → (physical delete after 30 days)
 * </pre>
 *
 * @author jsq
 */
public enum SessionStatus {

    /** Active session, visible in sidebar. */
    ACTIVE("活跃"),

    /** Archived session, collapsed in "Archived" section. */
    ARCHIVED("已归档"),

    /** Soft-deleted session, kept for 30 days before physical deletion. */
    DELETED("已删除");

    private final String displayName;

    SessionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
