package com.yupi.yuaiagent.artifact.adoption;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts machine-readable adoption evidence and removes it from user-visible text.
 */
public class ArtifactCitationExtractor {

    private static final Pattern MARKER = Pattern.compile(
            "\\s*<!--\\s*artifact-used\\s*:\\s*\\[([^]]*)]\\s*-->\\s*",
            Pattern.CASE_INSENSITIVE);

    public ExtractionResult extract(String answer, List<String> offeredArtifactIds) {
        String source = answer == null ? "" : answer;
        Set<String> offered = new HashSet<>(
                offeredArtifactIds == null ? List.of() : offeredArtifactIds);
        List<String> adopted = new ArrayList<>();
        Matcher matcher = MARKER.matcher(source);
        while (matcher.find()) {
            for (String raw : matcher.group(1).split(",")) {
                String id = raw.trim().replaceAll("^[\"']|[\"']$", "");
                if (offered.contains(id) && !adopted.contains(id)) {
                    adopted.add(id);
                }
            }
        }
        String clean = matcher.replaceAll("\n").trim();
        return new ExtractionResult(clean, List.copyOf(adopted));
    }

    public record ExtractionResult(String cleanText, List<String> adoptedArtifactIds) {
    }
}
