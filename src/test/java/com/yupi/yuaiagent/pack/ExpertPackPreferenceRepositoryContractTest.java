package com.yupi.yuaiagent.pack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpertPackPreferenceRepositoryContractTest {

    @TempDir
    Path tempDir;

    @Test
    void versionedSaveRejectsStaleWritesAndPreservesAllDisabled() {
        FileExpertPackPreferenceRepository repo = new FileExpertPackPreferenceRepository(
                tempDir.resolve("user-prefs.json"), new ObjectMapper());
        UserPackPreference first = repo.save(new UserPackPreference(
                "u1", PackPreferenceMode.EXPLICIT_ALL_DISABLED, Map.of("pack-a", false), 0));
        assertThat(first.version()).isEqualTo(1L);
        assertThatThrownBy(() -> repo.save(new UserPackPreference(
                "u1", PackPreferenceMode.EXPLICIT_PARTIAL, Map.of("pack-a", true), 0)))
                .isInstanceOf(IllegalStateException.class);
        UserPackPreference reloaded = repo.find("u1").orElseThrow();
        assertThat(reloaded.mode()).isEqualTo(PackPreferenceMode.EXPLICIT_ALL_DISABLED);
    }
}
