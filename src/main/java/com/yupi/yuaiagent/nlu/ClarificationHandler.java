package com.yupi.yuaiagent.nlu;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Template-based clarification — zero LLM calls.
 * Given known slots and missing required slots, generates a concise follow-up question.
 *
 * @author jsq
 */
@Component
public class ClarificationHandler {

    private static final Map<String, String> SLOT_QUESTIONS = Map.of(
        "entity",    "请问您想查询哪个主体？",
        "metric",    "请问您想查看什么指标？（如：ROI、消耗、转化率）",
        "timeRange", "请问您想看哪个时间范围？（如：近7天、昨天、本月）",
        "dimension", "请问您需要按什么维度查看？（如：按渠道、按城市）"
    );

    public String clarify(ConversationState knownSlots, List<String> missingRequired) {
        if (missingRequired == null || missingRequired.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();

        if (knownSlots.getEntity() != null) {
            sb.append("已知您要查询 ").append(knownSlots.getEntity()).append("，");
        }

        List<String> questions = missingRequired.stream()
            .limit(2)
            .map(slot -> SLOT_QUESTIONS.getOrDefault(slot, "请补充「" + slot + "」信息"))
            .toList();

        sb.append(String.join("，以及", questions));

        return sb.toString();
    }
}
