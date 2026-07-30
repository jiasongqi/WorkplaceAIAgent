package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.perception.DocumentPerceptionService;
import com.yupi.yuaiagent.perception.PerceptionCrossValidator;
import com.yupi.yuaiagent.perception.PerceptionResult;
import com.yupi.yuaiagent.perception.SessionUploadStore;
import com.yupi.yuaiagent.sessionstate.SessionSharedStateService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Perception application service — Controller → AppService → Domain.
 */
@Service
public class PerceptionAppService {

    private final DocumentPerceptionService documentPerceptionService;
    private final PerceptionCrossValidator perceptionCrossValidator;
    private final SessionSharedStateService sessionSharedStateService;
    private final SessionUploadStore sessionUploadStore;

    public PerceptionAppService(DocumentPerceptionService documentPerceptionService,
                                PerceptionCrossValidator perceptionCrossValidator,
                                SessionSharedStateService sessionSharedStateService,
                                SessionUploadStore sessionUploadStore) {
        this.documentPerceptionService = documentPerceptionService;
        this.perceptionCrossValidator = perceptionCrossValidator;
        this.sessionSharedStateService = sessionSharedStateService;
        this.sessionUploadStore = sessionUploadStore;
    }

    public Map<String, Object> preprocess(MultipartFile file, String hint) throws IOException {
        return toResponseMap(documentPerceptionService.perceive(file, hint), false);
    }

    /**
     * Preprocess and bind promptBlock into session Shared State so SSE GET
     * only carries a short user message (URL length safe).
     */
    public Map<String, Object> preprocessAndBind(MultipartFile file, String hint,
                                                 String chatId, String userId) throws IOException {
        if (!StringUtils.hasText(chatId)) {
            throw new IllegalArgumentException("chatId 不能为空");
        }
        PerceptionResult result = documentPerceptionService.perceive(file, hint);
        String uploadRef = sessionUploadStore.save(chatId, userId, file.getBytes(), result.filename());
        if (StringUtils.hasText(uploadRef)) {
            sessionSharedStateService.putFact(chatId, userId, "uploadRef", uploadRef);
        }
        sessionSharedStateService.setPerceptionBlock(chatId, userId, result.toPromptBlock());
        if (StringUtils.hasText(hint)) {
            String goal = "offer".equalsIgnoreCase(hint)
                    ? "分析 Offer/薪资材料并给出建议"
                    : "分析简历材料并给出可执行优化建议";
            sessionSharedStateService.setActiveGoal(chatId, userId, goal);
        }
        return toResponseMap(result, true);
    }

    public PerceptionCrossValidator.CrossCheckResult crossCheck(String hypothesis, String observed) {
        return perceptionCrossValidator.check(hypothesis, observed);
    }

    private static Map<String, Object> toResponseMap(PerceptionResult result, boolean bound) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", result.sourceType());
        body.put("filename", result.filename());
        body.put("confidence", result.confidence());
        body.put("injectionRisk", result.injectionRisk());
        body.put("structuredFields", result.structuredFields());
        body.put("notes", result.notes());
        String raw = result.rawText();
        body.put("rawTextPreview", raw.length() <= 500 ? raw : raw.substring(0, 500) + "…");
        body.put("promptBlock", result.toPromptBlock());
        body.put("boundToSession", bound);
        return body;
    }
}
