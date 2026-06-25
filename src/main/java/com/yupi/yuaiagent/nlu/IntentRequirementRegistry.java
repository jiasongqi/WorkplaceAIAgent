package com.yupi.yuaiagent.nlu;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Per-intent slot requirements — dual-dimension lookup (intent + routeHint).
 *
 * <p>Lookup order:
 * <ol>
 *   <li>routeHint-specific requirement (e.g., "advertiser.query.roi")</li>
 *   <li>intent-level fallback (e.g., "QUERY_DATA")</li>
 * </ol>
 *
 * @author jsq
 */
@Component
public class IntentRequirementRegistry {

    private final Map<String, IntentRequirement> requirements = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        // Intent-level requirements (Phase 1)
        register("QUERY_DATA",       List.of("entity"), List.of("metric", "timeRange", "dimension"));
        register("RESUME_OPTIMIZE",  List.of(), List.of());
        register("INTERVIEW_PREP",   List.of(), List.of());
        register("JOB_CHANGE",       List.of(), List.of());
        register("SALARY_ANALYZE",   List.of(), List.of("entity", "metric"));
        register("SALARY_NEGOTIATE", List.of(), List.of());
        register("LEAVE_PLAN",       List.of(), List.of());
        register("CONSULTATION",     List.of(), List.of());
        register("CAREER_GENERAL",   List.of(), List.of());

        // RouteHint-specific requirements (Phase 2)
        register("advertiser.query",      List.of("entity"), List.of("metric", "timeRange", "dimension"));
        register("advertiser.query.roi",  List.of("entity"), List.of("timeRange"));
        register("advertiser.analyze",    List.of("entity"), List.of("metric", "timeRange"));
        register("resume.optimize",       List.of(), List.of());
        register("resume.interview",      List.of(), List.of());
        register("salary.analyze",        List.of(), List.of("entity", "metric"));
        register("consultation.book",     List.of(), List.of());
        register("career.general",        List.of(), List.of());
    }

    public void register(String key, List<String> required, List<String> optional) {
        requirements.put(key, new IntentRequirement(required, optional));
    }

    /**
     * Find missing required slots. Tries routeHint first (exact → prefix fallback), then intent.
     */
    public List<String> findMissingRequired(String intent, String routeHint, ConversationState state) {
        IntentRequirement req = null;

        // 1. Exact routeHint match
        if (routeHint != null) {
            req = requirements.get(routeHint);
        }

        // 2. Prefix routeHint match (e.g., "advertiser.query.roi" → "advertiser.query")
        if (req == null && routeHint != null) {
            String prefix = routeHint;
            while (req == null && prefix.contains(".")) {
                prefix = prefix.substring(0, prefix.lastIndexOf("."));
                req = requirements.get(prefix);
            }
        }

        // 3. Intent-level fallback
        if (req == null) {
            req = requirements.get(intent);
        }
        if (req == null) return List.of();

        var missing = new ArrayList<String>();
        for (String slot : req.required()) {
            if (getSlotValue(state, slot) == null) {
                missing.add(slot);
            }
        }
        return missing;
    }

    /** Convenience overload for Phase 1 (no routeHint). */
    public List<String> findMissingRequired(String intent, ConversationState state) {
        return findMissingRequired(intent, null, state);
    }

    private Object getSlotValue(ConversationState state, String slot) {
        return switch (slot) {
            case "entity" -> state.getEntity();
            case "metric" -> state.getMetric();
            case "timeRange" -> state.getTimeRange();
            case "dimension" -> state.getDimension();
            default -> null;
        };
    }

    public record IntentRequirement(List<String> required, List<String> optional) {}
}
