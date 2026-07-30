package com.yupi.yuaiagent.rag.hybrid;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Text-first hybrid strategy: keyword overlap ranking; vision refs by caption overlap.
 * Ready to swap embeddings later without changing callers.
 */
@Component
public class TextFirstHybridRetrieval implements HybridRetrievalStrategy {

    @Override
    public HybridBundle retrieve(String query, List<TextHit> candidateTexts,
                                 List<VisionRef> candidateVisions, int topText, int topVision) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<TextHit> texts = new ArrayList<>();
        if (candidateTexts != null) {
            for (TextHit t : candidateTexts) {
                double score = t.score() > 0 ? t.score() : overlapScore(q, t.content());
                texts.add(new TextHit(t.content(), score, t.sourceId()));
            }
            texts.sort(Comparator.comparingDouble(TextHit::score).reversed());
            if (texts.size() > Math.max(1, topText)) {
                texts = new ArrayList<>(texts.subList(0, topText));
            }
        }
        List<VisionRef> visions = new ArrayList<>();
        if (candidateVisions != null) {
            for (VisionRef v : candidateVisions) {
                double score = v.score() > 0 ? v.score() : overlapScore(q, v.caption());
                visions.add(new VisionRef(v.caption(), v.imageUriOrPath(), score, v.sourceId()));
            }
            visions.sort(Comparator.comparingDouble(VisionRef::score).reversed());
            if (visions.size() > Math.max(0, topVision)) {
                visions = new ArrayList<>(visions.subList(0, topVision));
            }
        }
        return new HybridBundle(texts, visions);
    }

    private static double overlapScore(String query, String content) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(content)) {
            return 0;
        }
        String c = content.toLowerCase(Locale.ROOT);
        String[] tokens = query.split("\\s+|，|。|、|,|\\.");
        int hit = 0;
        int total = 0;
        for (String t : tokens) {
            if (t.length() < 2) {
                continue;
            }
            total++;
            if (c.contains(t)) {
                hit++;
            }
        }
        return total == 0 ? 0 : (double) hit / total;
    }
}
