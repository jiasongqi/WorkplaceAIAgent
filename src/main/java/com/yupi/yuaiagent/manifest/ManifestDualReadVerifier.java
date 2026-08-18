package com.yupi.yuaiagent.manifest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Runs the unified loader in observation mode while legacy registries remain authoritative.
 */
@Slf4j
@Component
public class ManifestDualReadVerifier {

    private final ManifestLoader loader;
    private final ManifestLoaderProperties properties;

    public ManifestDualReadVerifier(ManifestLoader loader, ManifestLoaderProperties properties) {
        this.loader = loader;
        this.properties = properties;
    }

    public <T> void verify(String catalog,
                           String resourcePattern,
                           Class<T> manifestType,
                           Function<T, String> keyExtractor,
                           Map<String, T> legacyItems,
                           ManifestLoadPolicy policy) {
        if (properties.getLoader() != ManifestLoaderProperties.Mode.DUAL) {
            return;
        }

        ManifestLoadReport<T> report = loader.load(resourcePattern, manifestType, keyExtractor, policy);
        report.errors().forEach(error ->
                log.warn("[ManifestDualRead] load error: catalog={}, resource={}, type={}, message={}, recovery={}",
                        catalog, error.resource(), error.manifestType(), error.message(), error.recommendation()));

        Set<String> legacyOnly = difference(legacyItems.keySet(), report.items().keySet());
        Set<String> unifiedOnly = difference(report.items().keySet(), legacyItems.keySet());
        Set<String> changed = new LinkedHashSet<>();
        legacyItems.forEach((key, value) -> {
            if (report.items().containsKey(key) && !Objects.equals(value, report.items().get(key))) {
                changed.add(key);
            }
        });

        if (legacyOnly.isEmpty() && unifiedOnly.isEmpty() && changed.isEmpty() && report.errors().isEmpty()) {
            log.info("[ManifestDualRead] parity verified: catalog={}, count={}, fingerprint={}",
                    catalog, report.items().size(), report.fingerprint());
            return;
        }
        log.warn("[ManifestDualRead] catalog drift: catalog={}, legacyOnly={}, unifiedOnly={}, changed={}, errors={}",
                catalog, legacyOnly, unifiedOnly, changed, report.errors().size());
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }
}
