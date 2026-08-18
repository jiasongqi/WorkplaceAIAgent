package com.yupi.yuaiagent.access;

import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeToolViews {

    private RuntimeToolViews() {
    }

    public static ToolCallback[] resolve(ToolCallback[] constructed) {
        RuntimeToolRequestContext.View view = RuntimeToolRequestContext.current().orElse(null);
        if (view == null || !view.filterEnabled()) {
            return constructed;
        }
        ToolCallback[] filtered = RuntimeToolFilter.filter(
                constructed, view.effectivePatterns(), view.preferenceMode());
        List<ToolCallback> wrapped = new ArrayList<>(filtered.length);
        for (ToolCallback callback : filtered) {
            wrapped.add(new PermissionCheckingToolCallback(callback));
        }
        return wrapped.toArray(ToolCallback[]::new);
    }
}
