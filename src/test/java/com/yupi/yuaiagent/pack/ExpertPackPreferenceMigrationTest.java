package com.yupi.yuaiagent.pack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpertPackPreferenceMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsLegacyFlatMapAsPartialOrAllDisabled() throws Exception {
        Path file = tempDir.resolve("user-prefs.json");
        Files.writeString(file, "{\"u1\":{\"career-resume\":false,\"career-escape\":false}}");
        FileExpertPackPreferenceRepository repo = new FileExpertPackPreferenceRepository(file, new ObjectMapper());
        UserPackPreference pref = repo.find("u1").orElseThrow();
        assertThat(pref.mode()).isEqualTo(PackPreferenceMode.EXPLICIT_ALL_DISABLED);
        assertThat(pref.packs()).containsEntry("career-resume", false);
    }
}
