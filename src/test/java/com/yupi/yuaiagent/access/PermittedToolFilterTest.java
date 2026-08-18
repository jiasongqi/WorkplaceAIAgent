package com.yupi.yuaiagent.access;

import com.yupi.yuaiagent.permission.PermissionAuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermittedToolFilterTest {

    @Test
    void nullDecisionServiceKeepsOnlyControlTools() {
        ToolCallback search = callback("searchWeb");
        ToolCallback terminate = callback("doTerminate");
        ToolCallback[] out = PermittedToolFilter.filter(null, "escape-agent", new ToolCallback[]{search, terminate});
        assertEquals(Set.of("doTerminate"), names(out));
    }

    @Test
    void hidesDeniedToolsAndKeepsAlwaysAllowed() {
        AccessDecisionService ads = denyAllExcept(Set.of("searchWeb"));
        ToolCallback[] input = {
                callback("searchWeb"),
                callback("executeTerminalCommand"),
                callback("doTerminate")
        };
        ToolCallback[] out = PermittedToolFilter.filter(ads, "escape-agent", input);
        Set<String> names = names(out);
        assertEquals(Set.of("searchWeb", "doTerminate"), names);
    }

    @Test
    void blankAgentCodeHidesEverythingExceptControlTools() {
        AccessDecisionService ads = denyAllExcept(Set.of("searchWeb"));
        ToolCallback[] input = {callback("searchWeb"), callback("doTerminate")};
        ToolCallback[] out = PermittedToolFilter.filter(ads, "", input);
        assertEquals(Set.of("doTerminate"), names(out));
    }

    private static AccessDecisionService denyAllExcept(Set<String> allowed) {
        AccessDecisionService ads = new AccessDecisionService();
        AccessVoter voter = new AccessVoter() {
            @Override
            public Vote vote(AccessDecisionContext context) {
                if (allowed.contains(context.getToolName())) {
                    return Vote.ALLOW;
                }
                return Vote.DENY;
            }

            @Override
            public String getName() {
                return "test-voter";
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(ads, "voters", List.of(voter));
        org.springframework.test.util.ReflectionTestUtils.setField(ads, "auditLog", new PermissionAuditLog());
        return ads;
    }

    private static ToolCallback callback(String name) {
        ToolCallback cb = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(cb.getToolDefinition()).thenReturn(def);
        return cb;
    }

    private static Set<String> names(ToolCallback[] tools) {
        return Arrays.stream(tools)
                .map(t -> t.getToolDefinition().name())
                .collect(Collectors.toSet());
    }
}
