package com.yupi.yuaiagent.agent.manifest;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.registry.AgentDescriptor;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Derives routing manifests from Agent YAML without copying runtime feedback weights.
 */
public final class AgentManifestFactory {

    private AgentManifestFactory() {
    }

    public static Map<AgentIntent, AgentManifest> legacyManifests() {
        Map<AgentIntent, AgentManifest> manifests = new EnumMap<>(AgentIntent.class);
        put(manifests, new AgentManifest(AgentIntent.RESUME, "简历优化专家",
                "擅长简历优化、求职、面试技巧、offer 选择。输入需要经历/目标岗位描述。",
                List.of("简历", "求职", "面试", "offer", "投递", "岗位"),
                List.of("text")));
        put(manifests, new AgentManifest(AgentIntent.NEGOTIATION, "薪资谈判专家",
                "擅长谈薪、涨薪、薪酬分析与话术。输入需要当前薪资/期望。",
                List.of("谈薪", "涨薪", "薪资", "薪酬", "年终奖", "hc"),
                List.of("text")));
        put(manifests, new AgentManifest(AgentIntent.ESCAPE, "离职规划专家",
                "擅长离职、辞职、劳动纠纷、工作交接。",
                List.of("离职", "辞职", "交接", "劳动", "仲裁", "竞业"),
                List.of("text")));
        put(manifests, new AgentManifest(AgentIntent.CONSULTATION, "预约咨询专家",
                "擅长预约咨询、查看日程、修改预约。输入需要姓名/联系方式/时间。",
                List.of("预约", "咨询", "日程", "日历", "约专家"),
                List.of("name_or_schedule")));
        put(manifests, new AgentManifest(AgentIntent.DATA_QUERY, "数据查询顾问",
                "擅长指标、报表、KPI、数据查询。",
                List.of("数据", "指标", "报表", "kpi", "统计"),
                List.of("query")));
        put(manifests, new AgentManifest(AgentIntent.DIGITAL_EMPLOYEE, "数字员工",
                "创建/调整专属数字员工人设。",
                List.of("数字员工", "专属员工", "人设"),
                List.of("persona")));
        put(manifests, new AgentManifest(AgentIntent.GENERAL, "职场通用顾问",
                "通用职场问题、人际关系、压力与职业规划兜底。",
                List.of("职场", "同事", "压力", "规划", "怎么办"),
                List.of("text")));
        return manifests;
    }

    public static Map<AgentIntent, AgentManifest> fromDescriptors(Collection<AgentDescriptor> descriptors) {
        Map<AgentIntent, AgentManifest> derived = new EnumMap<>(AgentIntent.class);
        if (descriptors == null) {
            return derived;
        }
        for (AgentDescriptor descriptor : descriptors) {
            AgentManifest manifest = fromDescriptor(descriptor);
            if (manifest != null) {
                derived.put(manifest.intent(), manifest);
            }
        }
        return derived;
    }

    public static AgentManifest fromDescriptor(AgentDescriptor descriptor) {
        if (descriptor == null || !descriptor.isEnabled()) {
            return null;
        }
        AgentIntent intent = parseIntent(descriptor);
        if (intent == null) {
            return null;
        }
        List<String> keywords = firstNonEmpty(descriptor.getRoutingKeywords(), descriptor.getIntentKeywords());
        List<String> inputs = firstNonEmpty(descriptor.getInputRequirements(), List.of("text"));
        String displayName = descriptor.getDisplayName() == null ? intent.getAgentName() : descriptor.getDisplayName();
        String description = descriptor.getDescription() == null ? intent.getDescription() : descriptor.getDescription();
        return new AgentManifest(intent, displayName, description, List.copyOf(keywords), List.copyOf(inputs));
    }

    public static StaticManifest staticView(AgentManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        return new StaticManifest(
                manifest.intent(),
                manifest.displayName(),
                manifest.description(),
                List.copyOf(manifest.keywords()),
                List.copyOf(manifest.requiredInputs())
        );
    }

    static AgentIntent parseIntent(AgentDescriptor descriptor) {
        String raw = descriptor.getIntent();
        if (raw != null && !raw.isBlank()) {
            try {
                return AgentIntent.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return switch (descriptor.getAgentCode() == null ? "" : descriptor.getAgentCode()) {
            case "resume-agent" -> AgentIntent.RESUME;
            case "negotiation-agent" -> AgentIntent.NEGOTIATION;
            case "escape-agent" -> AgentIntent.ESCAPE;
            case "consultation-agent" -> AgentIntent.CONSULTATION;
            case "data-agent" -> AgentIntent.DATA_QUERY;
            case "digital-employee" -> AgentIntent.DIGITAL_EMPLOYEE;
            case "general-agent" -> AgentIntent.GENERAL;
            default -> null;
        };
    }

    private static void put(Map<AgentIntent, AgentManifest> manifests, AgentManifest manifest) {
        manifests.put(manifest.intent(), manifest);
    }

    private static List<String> firstNonEmpty(List<String> preferred, List<String> fallback) {
        if (preferred != null && !preferred.isEmpty()) {
            return preferred;
        }
        return fallback == null ? List.of() : fallback;
    }

    public record StaticManifest(
            AgentIntent intent,
            String displayName,
            String description,
            List<String> keywords,
            List<String> requiredInputs
    ) {
    }
}
