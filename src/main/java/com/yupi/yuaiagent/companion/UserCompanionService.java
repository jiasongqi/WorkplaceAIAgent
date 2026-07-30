package com.yupi.yuaiagent.companion;

import com.yupi.yuaiagent.repository.entity.UserCompanionEntity;
import com.yupi.yuaiagent.repository.jpa.UserCompanionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserCompanionService {

    private final UserCompanionJpaRepository companionRepository;

    @Transactional
    public UserCompanionView getOrClaim(String userId) {
        return toView(companionRepository.findByUserId(userId).orElseGet(() -> createDefault(userId)));
    }

    @Transactional
    public UserCompanionView update(String userId, UpdateCompanionRequest request) {
        UserCompanionEntity entity = companionRepository.findByUserId(userId)
                .orElseGet(() -> createDefault(userId));
        if (StringUtils.hasText(request.displayName())) {
            entity.setDisplayName(request.displayName().trim());
        }
        if (request.personaPrompt() != null) {
            entity.setPersonaPrompt(request.personaPrompt());
        }
        if (request.stylePrefs() != null) {
            entity.setStylePrefs(new HashMap<>(request.stylePrefs()));
        }
        if (request.enabledSkills() != null) {
            entity.setEnabledSkills(new ArrayList<>(request.enabledSkills()));
        }
        entity.setVersion(entity.getVersion() == null ? 2 : entity.getVersion() + 1);
        return toView(companionRepository.save(entity));
    }

    /**
     * Prompt injection block for Orchestrator context.
     * Auto-claims a default companion on first use.
     */
    public String buildPromptInjection(String userId) {
        if (!StringUtils.hasText(userId)) {
            return "";
        }
        UserCompanionView view = getOrClaim(userId);
        StringBuilder sb = new StringBuilder();
        sb.append("【个人智能体伙伴】\n");
        sb.append("名称：").append(view.displayName()).append('\n');
        Map<String, Object> prefs = view.stylePrefs();
        if (prefs != null && !prefs.isEmpty()) {
            Object tone = prefs.get("tone");
            Object focus = prefs.get("focus");
            if (tone != null && StringUtils.hasText(tone.toString())) {
                sb.append("语气偏好：").append(tone).append('\n');
            }
            if (focus != null && StringUtils.hasText(focus.toString())) {
                sb.append("关注方向：").append(focus).append('\n');
            }
        }
        if (StringUtils.hasText(view.personaPrompt())) {
            sb.append("人设规则：").append(view.personaPrompt()).append('\n');
        }
        sb.append("请以该伙伴身份与用户对话，保持风格一致。\n");
        return sb.toString();
    }

    private UserCompanionEntity createDefault(String userId) {
        UserCompanionEntity entity = new UserCompanionEntity();
        entity.setUserId(userId);
        entity.setDisplayName("你的职场伙伴");
        entity.setPersonaPrompt("你是用户的专属职场伙伴：先给结论，再给可执行步骤；语气简洁、少客套；不确定时主动澄清。");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("tone", "简洁直接");
        prefs.put("focus", "简历、谈薪、职业方向");
        entity.setStylePrefs(prefs);
        entity.setEnabledSkills(new ArrayList<>());
        entity.setVersion(1);
        return companionRepository.save(entity);
    }

    private static UserCompanionView toView(UserCompanionEntity e) {
        return new UserCompanionView(
                e.getUserId(),
                e.getDisplayName(),
                e.getPersonaPrompt(),
                e.getStylePrefs(),
                e.getEnabledSkills() != null ? e.getEnabledSkills() : List.of(),
                e.getVersion()
        );
    }

    public record UserCompanionView(
            String userId,
            String displayName,
            String personaPrompt,
            Map<String, Object> stylePrefs,
            List<String> enabledSkills,
            Integer version
    ) {
    }

    public record UpdateCompanionRequest(
            String displayName,
            String personaPrompt,
            Map<String, Object> stylePrefs,
            List<String> enabledSkills
    ) {
    }
}
