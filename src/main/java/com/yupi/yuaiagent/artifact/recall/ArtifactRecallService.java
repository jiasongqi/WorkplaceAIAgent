package com.yupi.yuaiagent.artifact.recall;

import com.yupi.yuaiagent.artifact.ArtifactShelf;
import com.yupi.yuaiagent.artifact.ArtifactTypeCatalog;
import com.yupi.yuaiagent.artifact.model.Artifact;
import com.yupi.yuaiagent.artifact.model.ArtifactQuery;
import com.yupi.yuaiagent.artifact.model.ArtifactScope;
import com.yupi.yuaiagent.artifact.model.ArtifactStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Recalls published artifacts without mutating their lifecycle.
 */
public class ArtifactRecallService {

    private static final String HEADER = """
            【可复用交付物】
            以下候选仅供参考。实际采用时，请在回答末尾输出 <!--artifact-used:[artifactId,...]-->：
            """;

    private final ArtifactShelf artifactShelf;
    private final ArtifactTypeCatalog catalog;
    private final int topK;
    private final int characterBudget;

    public ArtifactRecallService(ArtifactShelf artifactShelf, ArtifactTypeCatalog catalog,
                                 int topK, int characterBudget) {
        this.artifactShelf = artifactShelf;
        this.catalog = catalog;
        this.topK = Math.max(1, topK);
        this.characterBudget = Math.max(0, characterBudget);
    }

    public RecallResult recall(String userId, String chatId, String targetAgent, String queryText) {
        if ((userId == null || userId.isBlank()) && (chatId == null || chatId.isBlank())) {
            return RecallResult.empty();
        }
        LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC());
        int candidateLimit = Math.max(topK * 4, topK);
        List<Artifact> candidates = new ArrayList<>();
        if (chatId != null && !chatId.isBlank()) {
            candidates.addAll(artifactShelf.query(ArtifactQuery.builder()
                    .userId(userId)
                    .chatId(chatId)
                    .scope(ArtifactScope.TASK)
                    .status(ArtifactStatus.PUBLISHED)
                    .reusable(true)
                    .targetAgent(targetAgent)
                    .activeAt(now)
                    .limit(candidateLimit)
                    .build()));
        }
        if (userId != null && !userId.isBlank()) {
            candidates.addAll(artifactShelf.query(ArtifactQuery.builder()
                    .userId(userId)
                    .scope(ArtifactScope.USER_PROFILE)
                    .status(ArtifactStatus.PUBLISHED)
                    .reusable(true)
                    .targetAgent(targetAgent)
                    .activeAt(now)
                    .limit(candidateLimit)
                    .build()));
        }

        Set<String> seen = new HashSet<>();
        List<Artifact> ranked = candidates.stream()
                .filter(a -> a != null && a.getArtifactId() != null)
                .filter(a -> seen.add(a.getArtifactId()))
                .filter(a -> eligible(a, targetAgent, now))
                .sorted(Comparator.comparingDouble(
                        (Artifact a) -> score(a, targetAgent, queryText, now)).reversed())
                .limit(topK)
                .toList();
        return render(ranked);
    }

    private boolean eligible(Artifact artifact, String targetAgent, LocalDateTime now) {
        if (artifact.getStatus() != ArtifactStatus.PUBLISHED || !artifact.isReusable()) {
            return false;
        }
        if (artifact.getExpiresAt() != null && !artifact.getExpiresAt().isAfter(now)) {
            return false;
        }
        if (targetAgent == null || targetAgent.isBlank()) {
            return true;
        }
        return artifact.getTargetAgents() != null
                && artifact.getTargetAgents().stream().anyMatch(targetAgent::equalsIgnoreCase);
    }

    private double score(Artifact artifact, String targetAgent, String queryText, LocalDateTime now) {
        double typeAffinity = catalog.find(artifact.getType())
                .filter(d -> targetAgent == null || d.targetAgents().stream()
                        .anyMatch(targetAgent::equalsIgnoreCase))
                .map(ignored -> 1.0)
                .orElse(0.0);
        Set<String> queryTokens = tokens(queryText);
        Set<String> artifactTokens = tokens(
                nullToEmpty(artifact.getTitle()) + " " + nullToEmpty(artifact.getSummary()));
        long overlap = artifactTokens.stream().filter(queryTokens::contains).count();
        double relevance = queryTokens.isEmpty() ? 0.0
                : (double) overlap / Math.max(1, queryTokens.size());
        long ageDays = artifact.getCreatedAt() == null ? 0
                : Math.max(0, Duration.between(artifact.getCreatedAt(), now).toDays());
        double recency = 1.0 / (1.0 + ageDays / 30.0);
        return typeAffinity * 0.35 + relevance * 0.5 + recency * 0.15;
    }

    private Set<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        Set<String> result = new LinkedHashSet<>();
        for (String word : normalized.split("\\s+")) {
            if (word.isBlank()) continue;
            result.add(word);
            int[] points = word.codePoints().toArray();
            for (int i = 0; i < points.length; i++) {
                result.add(new String(points, i, 1));
                if (i + 1 < points.length) {
                    result.add(new String(points, i, 2));
                }
            }
        }
        return result;
    }

    private RecallResult render(List<Artifact> artifacts) {
        if (artifacts.isEmpty() || characterBudget == 0) {
            return RecallResult.empty();
        }
        StringBuilder text = new StringBuilder();
        appendWithinBudget(text, HEADER, characterBudget);
        List<String> offeredIds = new ArrayList<>();
        for (int i = 0; i < artifacts.size() && text.length() < characterBudget; i++) {
            Artifact artifact = artifacts.get(i);
            String prefix = "[A" + (offeredIds.size() + 1) + "] id=" + artifact.getArtifactId()
                    + " type=" + artifact.getType()
                    + " title=" + nullToEmpty(artifact.getTitle())
                    + " summary=";
            int remaining = characterBudget - text.length();
            if (remaining <= prefix.length()) {
                break;
            }
            String summary = nullToEmpty(artifact.getSummary());
            int summaryBudget = Math.max(0, remaining - prefix.length() - 1);
            text.append(prefix)
                    .append(summary, 0, Math.min(summary.length(), summaryBudget))
                    .append('\n');
            offeredIds.add(artifact.getArtifactId());
        }
        return offeredIds.isEmpty() ? RecallResult.empty()
                : new RecallResult(text.toString(), List.copyOf(offeredIds));
    }

    private void appendWithinBudget(StringBuilder target, String value, int budget) {
        int remaining = budget - target.length();
        if (remaining > 0) {
            target.append(value, 0, Math.min(value.length(), remaining));
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
