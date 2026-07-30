package com.yupi.yuaiagent.service;

import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.skill.SkillDefinition;
import com.yupi.yuaiagent.skill.SkillRegistry;
import com.yupi.yuaiagent.trace.TraceRepository;
import com.yupi.yuaiagent.trace.model.ExecutionTrace;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SkillAppService {

    @Resource
    private SkillRegistry skillRegistry;
    @Resource
    private TraceRepository traceRepository;

    public List<Map<String, Object>> listSummaries() {
        return skillRegistry.getAll().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getName());
            m.put("description", s.getDescription());
            m.put("tags", s.getTags());
            m.put("version", s.getVersion());
            return m;
        }).toList();
    }

    public SkillDefinition draftFromTrace(String traceId) {
        ExecutionTrace trace = traceRepository.findById(traceId)
                .orElseThrow(() -> BusinessException.notFound("Trace"));
        List<TraceSpan> spans = trace.getSpans() != null ? trace.getSpans() : List.of();
        List<String> steps = new ArrayList<>();
        for (TraceSpan span : spans) {
            if (span.getStepType() == TraceStepType.TOOL_CALL
                    || span.getStepType() == TraceStepType.SKILL_MATCH
                    || span.getStepType() == TraceStepType.SUB_AGENT_EXECUTION) {
                steps.add("- [" + span.getStepType().name() + "] " + span.getLabel());
            }
        }
        if (steps.isEmpty()) {
            steps.add("- [GENERAL] 基于会话轨迹沉淀的通用职场技能");
        }

        String slug = "user-trace-" + (traceId.length() > 8 ? traceId.substring(0, 8) : traceId);
        SkillDefinition draft = new SkillDefinition();
        draft.setName(slug.toLowerCase().replaceAll("[^a-z0-9-]", "-"));
        draft.setDescription("从 Trace " + traceId + " 沉淀的技能，覆盖工具/专家执行步骤");
        draft.setVersion("0.1.0");
        draft.setAuthor("user");
        draft.setTags(List.of("user", "trace", "职场"));
        draft.setSystemPrompt("""
                你是根据历史成功执行轨迹沉淀的职场助手。
                请参考以下已验证步骤风格回答用户，保持可执行、结构化：
                %s
                """.formatted(String.join("\n", steps)));
        draft.setUserPromptTemplate("用户请求：\n{{input}}");
        return draft;
    }

    public SkillDefinition saveDraft(SkillDefinition draft) {
        if (draft == null || !StringUtils.hasText(draft.getName())) {
            throw BusinessException.badRequest("技能名称不能为空");
        }
        if (!StringUtils.hasText(draft.getSystemPrompt())) {
            throw BusinessException.badRequest("systemPrompt 不能为空");
        }
        if (draft.getTags() == null) {
            draft.setTags(List.of("user"));
        }
        skillRegistry.register(draft);
        return draft;
    }
}
