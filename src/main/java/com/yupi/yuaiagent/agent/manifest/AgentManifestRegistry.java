package com.yupi.yuaiagent.agent.manifest;

import com.yupi.yuaiagent.agent.AgentIntent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static Agent capability manifests + keyword/overlap scoring for low-confidence routing.
 */
@Component
public class AgentManifestRegistry {

    private final Map<AgentIntent, AgentManifest> manifests = new EnumMap<>(AgentIntent.class);
    /** Simple NACK feedback weights (lower = less preferred). */
    private final Map<AgentIntent, Double> feedbackBoost = new EnumMap<>(AgentIntent.class);

    public AgentManifestRegistry() {
        register(new AgentManifest(AgentIntent.RESUME, "简历优化专家",
                "擅长简历优化、求职、面试技巧、offer 选择。输入需要经历/目标岗位描述。",
                List.of("简历", "求职", "面试", "offer", "投递", "岗位"),
                List.of("text")));
        register(new AgentManifest(AgentIntent.NEGOTIATION, "薪资谈判专家",
                "擅长谈薪、涨薪、薪酬分析与话术。输入需要当前薪资/期望。",
                List.of("谈薪", "涨薪", "薪资", "薪酬", "年终奖", "hc"),
                List.of("text")));
        register(new AgentManifest(AgentIntent.ESCAPE, "离职规划专家",
                "擅长离职、辞职、劳动纠纷、工作交接。",
                List.of("离职", "辞职", "交接", "劳动", "仲裁", "竞业"),
                List.of("text")));
        register(new AgentManifest(AgentIntent.CONSULTATION, "预约咨询专家",
                "擅长预约咨询、查看日程、修改预约。输入需要姓名/联系方式/时间。",
                List.of("预约", "咨询", "日程", "日历", "约专家"),
                List.of("name_or_schedule")));
        register(new AgentManifest(AgentIntent.DATA_QUERY, "数据查询顾问",
                "擅长指标、报表、KPI、数据查询。",
                List.of("数据", "指标", "报表", "kpi", "统计"),
                List.of("query")));
        register(new AgentManifest(AgentIntent.DIGITAL_EMPLOYEE, "数字员工",
                "创建/调整专属数字员工人设。",
                List.of("数字员工", "专属员工", "人设"),
                List.of("persona")));
        register(new AgentManifest(AgentIntent.GENERAL, "职场通用顾问",
                "通用职场问题、人际关系、压力与职业规划兜底。",
                List.of("职场", "同事", "压力", "规划", "怎么办"),
                List.of("text")));
        for (AgentIntent i : AgentIntent.values()) {
            feedbackBoost.put(i, 1.0);
        }
    }

    public void register(AgentManifest manifest) {
        manifests.put(manifest.intent(), manifest);
    }

    public AgentManifest get(AgentIntent intent) {
        return manifests.get(intent);
    }

    public List<AgentManifest> all() {
        return new ArrayList<>(manifests.values());
    }

    /** Penalize an intent after NACK / quality failover (feedback loop). */
    public void penalize(AgentIntent intent, double factor) {
        if (intent == null) {
            return;
        }
        double cur = feedbackBoost.getOrDefault(intent, 1.0);
        feedbackBoost.put(intent, Math.max(0.3, cur * factor));
    }

    public void reward(AgentIntent intent, double factor) {
        if (intent == null) {
            return;
        }
        double cur = feedbackBoost.getOrDefault(intent, 1.0);
        feedbackBoost.put(intent, Math.min(1.5, cur * factor));
    }

    /**
     * Rank manifests by keyword overlap × feedback boost.
     */
    public List<ScoredManifest> rank(String userMessage) {
        String msg = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        List<ScoredManifest> scored = new ArrayList<>();
        for (AgentManifest m : manifests.values()) {
            if (m.intent() == AgentIntent.GENERAL) {
                continue; // GENERAL is fallback, not primary semantic pick
            }
            double hits = 0;
            for (String kw : m.keywords()) {
                if (msg.contains(kw.toLowerCase(Locale.ROOT))) {
                    hits += 1.0;
                }
            }
            if (hits <= 0) {
                continue;
            }
            // Absolute hit count × feedback (not diluted by long keyword lists)
            double score = hits * feedbackBoost.getOrDefault(m.intent(), 1.0);
            scored.add(new ScoredManifest(m, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredManifest::score).reversed());
        return scored;
    }

    /**
     * Pick best specialist when confidence is low; empty → keep caller default.
     */
    public AgentIntent suggest(String userMessage, double minScore) {
        List<ScoredManifest> ranked = rank(userMessage);
        if (ranked.isEmpty() || ranked.get(0).score() < minScore) {
            return null;
        }
        return ranked.get(0).manifest().intent();
    }

    public record ScoredManifest(AgentManifest manifest, double score) {}
}
