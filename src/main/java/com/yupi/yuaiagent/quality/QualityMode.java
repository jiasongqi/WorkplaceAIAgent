package com.yupi.yuaiagent.quality;

/**
 * Quality review running mode.
 *
 * @author jsq
 */
public enum QualityMode {

    /** No review — fastest response for casual chat. */
    OFF("关闭"),

    /** Auto-detect via QualityModeResolver (default). */
    AUTO("自动"),

    /** Force single review pass. */
    REVIEW("审查模式"),

    /** Force red-team adversarial loop. */
    RED_TEAM("红蓝对抗");

    private final String displayName;

    QualityMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
