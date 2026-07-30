package com.yupi.yuaiagent.perception;

import com.yupi.yuaiagent.rag.hybrid.HybridRetrievalStrategy;
import com.yupi.yuaiagent.rag.hybrid.TextFirstHybridRetrieval;
import com.yupi.yuaiagent.sessionstate.SessionSharedState;
import com.yupi.yuaiagent.sessionstate.SessionSharedStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Query-aware hybrid retrieval over bound perception material (Ch5 multi-route recall lite).
 */
@Slf4j
@Service
public class PerceptionHybridContextService {

    private static final Pattern EXTRACTED_TEXT = Pattern.compile(
            "- 提取文本：\\s*\\n([\\s\\S]*?)(?=\\n- |\\z)");
    private static final Pattern STRUCTURED_FIELD = Pattern.compile("·\\s*(\\w+)=(.+)");

    private final SessionSharedStateService sessionSharedStateService;
    private final HybridRetrievalStrategy hybridRetrieval;
    private final int topText;
    private final int topVision;

    public PerceptionHybridContextService(SessionSharedStateService sessionSharedStateService,
                                          TextFirstHybridRetrieval hybridRetrieval,
                                          @Value("${perception.hybrid.top-text:3}") int topText,
                                          @Value("${perception.hybrid.top-vision:2}") int topVision) {
        this.sessionSharedStateService = sessionSharedStateService;
        this.hybridRetrieval = hybridRetrieval;
        this.topText = Math.max(1, topText);
        this.topVision = Math.max(0, topVision);
    }

    public String buildHybridContext(String chatId, String userId, String query) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(query)) {
            return "";
        }
        SessionSharedState state = sessionSharedStateService.getOrCreate(chatId, userId);
        String block = state.getLastPerceptionBlock();
        if (!StringUtils.hasText(block)) {
            return "";
        }

        List<HybridRetrievalStrategy.TextHit> texts = extractTextHits(block);
        List<HybridRetrievalStrategy.VisionRef> visions = extractVisionRefs(block, state);
        if (texts.isEmpty() && visions.isEmpty()) {
            return "";
        }

        HybridRetrievalStrategy.HybridBundle bundle =
                hybridRetrieval.retrieve(query, texts, visions, topText, topVision);
        if (bundle.textHits().isEmpty() && bundle.visionRefs().isEmpty()) {
            return "";
        }
        log.info("[PerceptionHybrid] chatId={} textHits={} visionRefs={}",
                chatId, bundle.textHits().size(), bundle.visionRefs().size());
        return bundle.toPromptContext(topVision);
    }

    private static List<HybridRetrievalStrategy.TextHit> extractTextHits(String block) {
        List<HybridRetrievalStrategy.TextHit> hits = new ArrayList<>();
        Matcher m = EXTRACTED_TEXT.matcher(block);
        if (m.find()) {
            String body = m.group(1).trim();
            if (StringUtils.hasText(body)) {
                hits.add(new HybridRetrievalStrategy.TextHit(body, 0, "perception-text"));
            }
        }
        Matcher fieldMatcher = STRUCTURED_FIELD.matcher(block);
        while (fieldMatcher.find()) {
            hits.add(new HybridRetrievalStrategy.TextHit(
                    fieldMatcher.group(1) + "=" + fieldMatcher.group(2).trim(),
                    0,
                    "perception-field-" + fieldMatcher.group(1)));
        }
        if (hits.isEmpty() && block.length() > 50) {
            hits.add(new HybridRetrievalStrategy.TextHit(block, 0, "perception-block"));
        }
        return hits;
    }

    private static List<HybridRetrievalStrategy.VisionRef> extractVisionRefs(
            String block, SessionSharedState state) {
        List<HybridRetrievalStrategy.VisionRef> refs = new ArrayList<>();
        if (!block.contains("来源：image") && !block.toLowerCase(Locale.ROOT).contains("sourceType=image")) {
            return refs;
        }
        String caption = null;
        Matcher cap = Pattern.compile("imageCaption=([^\\n]+)").matcher(block);
        if (cap.find()) {
            caption = cap.group(1).trim();
        }
        if (!StringUtils.hasText(caption)) {
            caption = "用户上传的图片材料";
        }
        String ref = state.getFacts() != null ? state.getFacts().get("uploadRef") : null;
        if (!StringUtils.hasText(ref)) {
            ref = "session://perception-image";
        }
        refs.add(new HybridRetrievalStrategy.VisionRef(caption, ref, 0, "perception-image"));
        return refs;
    }
}
