package com.yupi.yuaiagent.nlu;

import org.springframework.stereotype.Component;

/**
 * Generates route hints using dot-notation template.
 *
 * <p>Format: {@code {domain}.{action}.{qualifier?}}
 *
 * <p>Examples:
 * <ul>
 *   <li>domain=ADVERTISER, action=QUERY, metric=ROI → "advertiser.query.roi"</li>
 *   <li>domain=ADVERTISER, action=QUERY, metric=null → "advertiser.query"</li>
 *   <li>domain=RESUME, action=OPTIMIZE → "resume.optimize"</li>
 * </ul>
 *
 * <p>WorkflowMatcher can do prefix matching: "advertiser.query.*" catches all data queries.
 *
 * @author jsq
 */
@Component
public class RouteTemplate {

    /**
     * Generate a dotted route hint from domain + action + optional metric.
     *
     * @param domain LLM output (e.g., "ADVERTISER")
     * @param action LLM output (e.g., "QUERY")
     * @param metric extracted metric (e.g., "ROI"), nullable
     * @return dotted route string, or null if domain/action missing
     */
    public String resolve(String domain, String action, String metric) {
        if (domain == null || action == null) return null;

        String route = domain.toLowerCase() + "." + action.toLowerCase();

        if (metric != null && !metric.isBlank()) {
            route += "." + metric.toLowerCase().replaceAll("[^a-z0-9]", "");
        }

        return route;
    }
}
