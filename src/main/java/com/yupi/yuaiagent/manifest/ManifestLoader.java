package com.yupi.yuaiagent.manifest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Shared deterministic YAML loader for declarative platform manifests.
 */
@Component
public class ManifestLoader {

    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yamlMapper;

    public ManifestLoader() {
        this(new PathMatchingResourcePatternResolver());
    }

    ManifestLoader(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = Objects.requireNonNull(resourceResolver, "resourceResolver");
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public <T> ManifestLoadReport<T> load(String resourcePattern,
                                          Class<T> manifestType,
                                          Function<T, String> keyExtractor,
                                          ManifestLoadPolicy policy) {
        Objects.requireNonNull(resourcePattern, "resourcePattern");
        Objects.requireNonNull(manifestType, "manifestType");
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        Objects.requireNonNull(policy, "policy");

        Map<String, T> items = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        List<ManifestLoadError> errors = new ArrayList<>();
        MessageDigest digest = sha256();

        try {
            Resource[] resources = resourceResolver.getResources(resourcePattern);
            Arrays.sort(resources, Comparator.comparing(this::resourceName));
            for (Resource resource : resources) {
                loadResource(resource, manifestType, keyExtractor, items, sources, errors, digest);
            }
        } catch (Exception exception) {
            errors.add(error(resourcePattern, manifestType, exception));
        }

        if (policy == ManifestLoadPolicy.STRICT && !errors.isEmpty()) {
            throw new ManifestLoadException(resourcePattern, errors);
        }
        return new ManifestLoadReport<>(items, sources, errors, HexFormat.of().formatHex(digest.digest()));
    }

    private <T> void loadResource(Resource resource,
                                  Class<T> manifestType,
                                  Function<T, String> keyExtractor,
                                  Map<String, T> items,
                                  Map<String, String> sources,
                                  List<ManifestLoadError> errors,
                                  MessageDigest digest) {
        String name = resourceName(resource);
        try {
            byte[] content = resource.getContentAsByteArray();
            digest.update(fingerprintName(resource).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(content);
            digest.update((byte) 0);

            T item = yamlMapper.readValue(content, manifestType);
            String key = item == null ? null : keyExtractor.apply(item);
            if (key == null || key.isBlank()) {
                errors.add(error(name, manifestType, "manifest key is missing"));
            } else if (items.containsKey(key)) {
                errors.add(error(name, manifestType, "duplicate manifest key: " + key));
            } else {
                items.put(key, item);
                sources.put(key, name);
            }
        } catch (Exception exception) {
            errors.add(error(name, manifestType, exception));
        }
    }

    private String resourceName(Resource resource) {
        try {
            return resource.getURL().toExternalForm();
        } catch (Exception ignored) {
            return resource.getDescription();
        }
    }

    private String fingerprintName(Resource resource) {
        String filename = resource.getFilename();
        return filename == null ? resource.getDescription() : filename;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static ManifestLoadError error(String resource, Class<?> manifestType, Exception exception) {
        return error(resource, manifestType, safeMessage(exception));
    }

    private static ManifestLoadError error(String resource, Class<?> manifestType, String message) {
        return new ManifestLoadError(
                resource,
                manifestType.getSimpleName(),
                message,
                "Fix the YAML resource and restart with platform.manifest.loader=legacy if rollback is required"
        );
    }
}
