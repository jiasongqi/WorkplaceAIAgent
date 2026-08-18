package com.yupi.yuaiagent.manifest;

import com.yupi.yuaiagent.permission.model.PermissionProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ManifestDualReadVerifierTest {

    @Test
    void legacyModeDoesNotInvokeUnifiedLoader() {
        ManifestLoader loader = mock(ManifestLoader.class);
        ManifestLoaderProperties properties = new ManifestLoaderProperties();
        ManifestDualReadVerifier verifier = new ManifestDualReadVerifier(loader, properties);

        verifier.verify("permissions", "classpath:permissions/*.yaml",
                PermissionProfile.class, PermissionProfile::getAgentCode, Map.of(), ManifestLoadPolicy.STRICT);

        verifyNoInteractions(loader);
    }

    @Test
    void dualModePreservesTheCatalogSpecificStrictnessPolicy() {
        ManifestLoader loader = mock(ManifestLoader.class);
        ManifestLoaderProperties properties = new ManifestLoaderProperties();
        properties.setLoader(ManifestLoaderProperties.Mode.DUAL);
        ManifestDualReadVerifier verifier = new ManifestDualReadVerifier(loader, properties);
        String pattern = "classpath:permissions/*.yaml";
        when(loader.load(eq(pattern), eq(PermissionProfile.class),
                org.mockito.ArgumentMatchers.<java.util.function.Function<PermissionProfile, String>>any(),
                eq(ManifestLoadPolicy.STRICT)))
                .thenReturn(new ManifestLoadReport<>(Map.of(), Map.of(), List.of(), "fingerprint"));

        verifier.verify("permissions", pattern, PermissionProfile.class,
                PermissionProfile::getAgentCode, Map.of(), ManifestLoadPolicy.STRICT);

        verify(loader).load(eq(pattern), eq(PermissionProfile.class),
                org.mockito.ArgumentMatchers.<java.util.function.Function<PermissionProfile, String>>any(),
                eq(ManifestLoadPolicy.STRICT));
    }

    @Test
    void strictPermissionFailureIntentionallyBlocksDualModeStartup() {
        ManifestLoader loader = mock(ManifestLoader.class);
        ManifestLoaderProperties properties = new ManifestLoaderProperties();
        properties.setLoader(ManifestLoaderProperties.Mode.DUAL);
        ManifestDualReadVerifier verifier = new ManifestDualReadVerifier(loader, properties);
        String pattern = "classpath:permissions/*.yaml";
        ManifestLoadError error = new ManifestLoadError(
                "invalid.yaml", "PermissionProfile", "unknown field", "fix the YAML");
        when(loader.load(eq(pattern), eq(PermissionProfile.class),
                org.mockito.ArgumentMatchers.<java.util.function.Function<PermissionProfile, String>>any(),
                eq(ManifestLoadPolicy.STRICT)))
                .thenThrow(new ManifestLoadException(pattern, List.of(error)));

        assertThrows(ManifestLoadException.class, () -> verifier.verify(
                "permissions", pattern, PermissionProfile.class,
                PermissionProfile::getAgentCode, Map.of(), ManifestLoadPolicy.STRICT));
    }

    @Test
    void unsupportedUnifiedModeIsRejectedExplicitly() {
        ManifestLoaderProperties properties = new ManifestLoaderProperties();
        properties.setLoader(ManifestLoaderProperties.Mode.UNIFIED);

        assertThrows(IllegalStateException.class, properties::validateSupportedMode);
    }
}
