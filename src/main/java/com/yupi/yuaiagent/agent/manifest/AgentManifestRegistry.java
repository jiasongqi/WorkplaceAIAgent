package com.yupi.yuaiagent.agent.manifest;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.registry.AgentDescriptor;
import com.yupi.yuaiagent.registry.AgentRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Static Agent capability manifests + keyword/overlap scoring for low-confidence routing.
 */
@Slf4j
@Component
public class AgentManifestRegistry {

    private final Map<AgentIntent, AgentManifest> legacyManifests;
    private final AtomicReference<Map<AgentIntent, AgentManifest>> yamlManifests =
            new AtomicReference<>(Map.of());
    private final ConcurrentHashMap<AgentIntent, Double> feedbackBoost = new ConcurrentHashMap<>();
    private final AgentMetadataProperties.Source metadataSource;

    public AgentManifestRegistry() {
        this(AgentMetadataProperties.Source.LEGACY, List.of());
    }

    @Autowired
    public AgentManifestRegistry(AgentMetadataProperties properties, AgentRegistry agentRegistry) {
        this(properties.getMetadataSource(), agentRegistry.list());
    }

    public AgentManifestRegistry(AgentMetadataProperties.Source metadataSource,
                                 Collection<AgentDescriptor> descriptors) {
        this.metadataSource = metadataSource == null ? AgentMetadataProperties.Source.LEGACY : metadataSource;
        this.legacyManifests = Map.copyOf(AgentManifestFactory.legacyManifests());
        replaceYamlManifests(descriptors);
        for (AgentIntent intent : AgentIntent.values()) {
            feedbackBoost.putIfAbsent(intent, 1.0);
        }
        if (this.metadataSource == AgentMetadataProperties.Source.SHADOW) {
            logShadowDifferences();
        }
    }

    public void reloadFrom(Collection<AgentDescriptor> descriptors) {
        replaceYamlManifests(descriptors);
        if (metadataSource == AgentMetadataProperties.Source.SHADOW) {
            logShadowDifferences();
        }
    }

    public void register(AgentManifest manifest) {
        yamlManifests.updateAndGet(current -> {
            Map<AgentIntent, AgentManifest> next = new EnumMap<>(AgentIntent.class);
            next.putAll(current);
            next.put(manifest.intent(), manifest);
            return Map.copyOf(next);
        });
    }

    public AgentManifest get(AgentIntent intent) {
        return routingManifests().get(intent);
    }

    public List<AgentManifest> all() {
        return new ArrayList<>(routingManifests().values());
    }

    /** Penalize an intent after NACK / quality failover (feedback loop). */
    public void penalize(AgentIntent intent, double factor) {
        if (intent == null) {
            return;
        }
        feedbackBoost.compute(intent, (key, current) -> Math.max(0.3, defaultBoost(current) * factor));
    }

    public void reward(AgentIntent intent, double factor) {
        if (intent == null) {
            return;
        }
        feedbackBoost.compute(intent, (key, current) -> Math.min(1.5, defaultBoost(current) * factor));
    }

    /**
     * Rank manifests by keyword overlap × feedback boost.
     */
    public List<ScoredManifest> rank(String userMessage) {
        String msg = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        List<ScoredManifest> scored = new ArrayList<>();
        for (AgentManifest m : routingManifests().values()) {
            if (m.intent() == AgentIntent.GENERAL) {
                continue;
            }
            double hits = 0;
            for (String kw : m.keywords()) {
                if (msg.contains(kw.toLowerCase(Locale.ROOT))) {
                    hits += 1.0;
                }
            }
            if (hits <= 0) {
                continue;
            }
            double score = hits * feedbackBoost.getOrDefault(m.intent(), 1.0);
            scored.add(new ScoredManifest(m, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredManifest::score).reversed());
        return scored;
    }

    /**
     * Pick best specialist when confidence is low; empty → keep caller default.
     */
    public AgentIntent suggest(String userMessage, double minScore) {
        List<ScoredManifest> ranked = rank(userMessage);
        if (ranked.isEmpty() || ranked.get(0).score() < minScore) {
            return null;
        }
        return ranked.get(0).manifest().intent();
    }

    public record ScoredManifest(AgentManifest manifest, double score) {}

    private Map<AgentIntent, AgentManifest> routingManifests() {
        return metadataSource == AgentMetadataProperties.Source.REGISTRY
                ? yamlManifests.get()
                : legacyManifests;
    }

    private void replaceYamlManifests(Collection<AgentDescriptor> descriptors) {
        yamlManifests.set(Map.copyOf(AgentManifestFactory.fromDescriptors(descriptors)));
    }

    private void logShadowDifferences() {
        Map<AgentIntent, AgentManifest> yaml = yamlManifests.get();
        for (AgentIntent intent : AgentIntent.values()) {
            AgentManifest legacy = legacyManifests.get(intent);
            AgentManifest derived = yaml.get(intent);
            if (legacy == null || derived == null) {
                log.warn("Agent manifest shadow missing intent={}", intent);
                continue;
            }
            var left = AgentManifestFactory.staticView(legacy);
            var right = AgentManifestFactory.staticView(derived);
            if (!left.equals(right)) {
                log.warn("Agent manifest shadow mismatch intent={} legacy={} yaml={}", intent, left, right);
            }
        }
    }

    private static double defaultBoost(Double current) {
        return current == null ? 1.0 : current;
    }
}
