package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.pack.PackPreferenceMode;
import com.yupi.yuaiagent.permission.model.PermissionProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PackAllDisabledPermissionTest {

    @Test
    void effectiveSubsetOfBaseAndAllDisabledIsControlOnly() {
        PermissionProfile base = PermissionProfile.builder()
                .agentCode("negotiation-agent")
                .allowedToolPatterns(Set.of("searchWeb", "searchKnowledgeBase"))
                .build();
        Set<String> partial = PermissionNarrowingService.effectivePatterns(
                base, Set.of("searchWeb"), PackPreferenceMode.EXPLICIT_PARTIAL);
        assertThat(partial).isSubsetOf(Set.of("searchWeb", "searchKnowledgeBase", "doTerminate", "checkAsyncToolTask"));
        Set<String> disabled = PermissionNarrowingService.effectivePatterns(
                base, Set.of("searchWeb"), PackPreferenceMode.EXPLICIT_ALL_DISABLED);
        assertThat(disabled).containsExactlyInAnyOrder("doTerminate", "checkAsyncToolTask");
    }
}
