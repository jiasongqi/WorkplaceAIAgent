package com.yupi.yuaiagent.manifest;

import com.yupi.yuaiagent.permission.model.PermissionProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsValidYamlAndProducesStableFingerprint() throws Exception {
        Files.writeString(tempDir.resolve("resume.yaml"), """
                agentCode: resume-agent
                displayName: Resume
                allowedToolPatterns:
                  - rag.query
                """);
        ManifestLoader loader = new ManifestLoader();

        ManifestLoadReport<PermissionProfile> first = loader.load(
                pattern(), PermissionProfile.class, PermissionProfile::getAgentCode, ManifestLoadPolicy.LENIENT);
        ManifestLoadReport<PermissionProfile> second = loader.load(
                pattern(), PermissionProfile.class, PermissionProfile::getAgentCode, ManifestLoadPolicy.LENIENT);

        assertEquals(1, first.items().size());
        assertTrue(first.items().containsKey("resume-agent"));
        assertFalse(first.fingerprint().isBlank());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertTrue(first.errors().isEmpty());
    }

    @Test
    void lenientModeSkipsMalformedFilesAndReportsTheirResource() throws Exception {
        Files.writeString(tempDir.resolve("valid.yaml"), "agentCode: resume-agent\n");
        Files.writeString(tempDir.resolve("invalid.yaml"), "agentCode: [not-valid\n");

        ManifestLoadReport<PermissionProfile> result = new ManifestLoader().load(
                pattern(), PermissionProfile.class, PermissionProfile::getAgentCode, ManifestLoadPolicy.LENIENT);

        assertEquals(1, result.items().size());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().resource().contains("invalid.yaml"));
    }

    @Test
    void strictModeRejectsMalformedFiles() throws Exception {
        Files.writeString(tempDir.resolve("invalid.yaml"), "unknownField: true\n");

        assertThrows(ManifestLoadException.class, () -> new ManifestLoader().load(
                pattern(), PermissionProfile.class, PermissionProfile::getAgentCode, ManifestLoadPolicy.STRICT));
    }

    @Test
    void jacksonDefaultsRemainIntactWhenFieldsAreOmitted() throws Exception {
        Files.writeString(tempDir.resolve("minimal.yaml"), "agentCode: resume-agent\n");

        PermissionProfile profile = new ManifestLoader().load(
                        pattern(), PermissionProfile.class, PermissionProfile::getAgentCode, ManifestLoadPolicy.LENIENT)
                .items()
                .get("resume-agent");

        assertEquals(20, profile.getMaxToolCallsPerRequest());
        assertEquals("1.0", profile.getVersion());
    }

    @Test
    void fingerprintDoesNotDependOnAbsoluteResourceLocation() throws Exception {
        Path firstDirectory = Files.createDirectory(tempDir.resolve("first"));
        Path secondDirectory = Files.createDirectory(tempDir.resolve("second"));
        String yaml = "agentCode: resume-agent\n";
        Files.writeString(firstDirectory.resolve("profile.yaml"), yaml);
        Files.writeString(secondDirectory.resolve("profile.yaml"), yaml);
        ManifestLoader loader = new ManifestLoader();

        String first = loader.load(firstDirectory.toUri() + "*.yaml", PermissionProfile.class,
                PermissionProfile::getAgentCode, ManifestLoadPolicy.LENIENT).fingerprint();
        String second = loader.load(secondDirectory.toUri() + "*.yaml", PermissionProfile.class,
                PermissionProfile::getAgentCode, ManifestLoadPolicy.LENIENT).fingerprint();

        assertEquals(first, second);
    }

    private String pattern() {
        return tempDir.toUri() + "*.yaml";
    }
}
