package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.pack.PackPreferenceMode;
import com.yupi.yuaiagent.permission.model.PermissionProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionNarrowingServiceTest {

    @Test
    void allDisabledLeavesOnlyControlTools() {
        PermissionProfile base = PermissionProfile.builder()
                .agentCode("resume-agent")
                .allowedToolPatterns(Set.of("searchKnowledgeBase", "readFile"))
                .build();
        Set<String> effective = PermissionNarrowingService.effectivePatterns(
                base, Set.of("searchKnowledgeBase"), PackPreferenceMode.EXPLICIT_ALL_DISABLED);
        assertThat(effective).containsExactlyInAnyOrder("doTerminate", "checkAsyncToolTask");
        assertThat(PermissionNarrowingService.allows(effective, "searchKnowledgeBase")).isFalse();
        assertThat(PermissionNarrowingService.allows(effective, "doTerminate")).isTrue();
    }

    @Test
    void intersectionStaysSubsetOfBase() {
        PermissionProfile base = PermissionProfile.builder()
                .agentCode("resume-agent")
                .allowedToolPatterns(Set.of("searchKnowledgeBase", "readFile"))
                .build();
        Set<String> effective = PermissionNarrowingService.effectivePatterns(
                base, Set.of("searchKnowledgeBase", "writeFile"), PackPreferenceMode.EXPLICIT_PARTIAL);
        assertThat(effective).contains("searchKnowledgeBase");
        assertThat(effective).doesNotContain("writeFile");
    }

    @Test
    void nonAdminNakedWildcardIsRejected() {
        PermissionProfile profile = PermissionProfile.builder()
                .agentCode("general-agent")
                .admin(false)
                .allowedToolPatterns(Set.of("*"))
                .build();
        assertThatThrownBy(() -> PermissionNarrowingService.rejectNakedWildcard(profile))
                .isInstanceOf(IllegalStateException.class);
    }
}
