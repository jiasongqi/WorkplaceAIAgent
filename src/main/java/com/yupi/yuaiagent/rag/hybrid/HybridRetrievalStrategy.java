package com.yupi.yuaiagent.rag.hybrid;

import java.util.List;

/**
 * Hybrid retrieval: text chunks first, optional vision/caption refs second
 * (mm_agent_tutorial Ch1 Ex 1.6 — don't dump every PDF page into VLM context).
 */
public interface HybridRetrievalStrategy {

    record TextHit(String content, double score, String sourceId) {
    }

    record VisionRef(String caption, String imageUriOrPath, double score, String sourceId) {
    }

    record HybridBundle(List<TextHit> textHits, List<VisionRef> visionRefs) {
        public HybridBundle {
            textHits = textHits == null ? List.of() : List.copyOf(textHits);
            visionRefs = visionRefs == null ? List.of() : List.copyOf(visionRefs);
        }

        /** Prompt-ready text; vision refs listed as captions only (URI for future VLM). */
        public String toPromptContext(int maxVision) {
            StringBuilder sb = new StringBuilder();
            sb.append("【混合检索 Hybrid Retrieval】\n");
            if (!textHits.isEmpty()) {
                sb.append("- 文本命中：\n");
                for (int i = 0; i < textHits.size(); i++) {
                    TextHit t = textHits.get(i);
                    String body = t.content() != null && t.content().length() > 800
                            ? t.content().substring(0, 800) + "…" : t.content();
                    sb.append("  ").append(i + 1).append(". ").append(body).append('\n');
                }
            }
            int visionLimit = Math.max(0, maxVision);
            if (!visionRefs.isEmpty() && visionLimit > 0) {
                sb.append("- 视觉引用（仅 Top-").append(visionLimit).append("，勿全量塞图）：\n");
                for (int i = 0; i < Math.min(visionLimit, visionRefs.size()); i++) {
                    VisionRef v = visionRefs.get(i);
                    sb.append("  ").append(i + 1).append(". caption=")
                            .append(v.caption())
                            .append(" | ref=").append(v.imageUriOrPath()).append('\n');
                }
            }
            return sb.toString();
        }
    }

    HybridBundle retrieve(String query, List<TextHit> candidateTexts, List<VisionRef> candidateVisions,
                          int topText, int topVision);
}
