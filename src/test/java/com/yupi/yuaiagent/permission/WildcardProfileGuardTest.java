package com.yupi.yuaiagent.permission;

import com.yupi.yuaiagent.permission.model.PermissionProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WildcardProfileGuardTest {

    @Test
    void adminMayUseNakedWildcard() {
        PermissionProfile admin = PermissionProfile.builder()
                .agentCode("admin-agent")
                .admin(true)
                .allowedToolPatterns(Set.of("*"))
                .build();
        assertThatCode(() -> PermissionNarrowingService.rejectNakedWildcard(admin)).doesNotThrowAnyException();
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
