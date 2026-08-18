package com.yupi.yuaiagent.pack;

import java.util.Optional;

public interface ExpertPackPreferenceRepository {
    Optional<UserPackPreference> find(String userId);

    UserPackPreference save(UserPackPreference preference);
}
