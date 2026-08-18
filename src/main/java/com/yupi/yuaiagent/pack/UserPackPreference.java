package com.yupi.yuaiagent.pack;

import java.util.Map;

public record UserPackPreference(
        String userId,
        PackPreferenceMode mode,
        Map<String, Boolean> packs,
        long version
) {
    public UserPackPreference {
        packs = packs == null ? Map.of() : Map.copyOf(packs);
        mode = mode == null ? PackPreferenceMode.UNSET : mode;
    }
}
