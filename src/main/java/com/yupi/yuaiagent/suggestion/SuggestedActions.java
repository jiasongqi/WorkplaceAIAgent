package com.yupi.yuaiagent.suggestion;

import com.yupi.yuaiagent.agent.AgentIntent;

import java.util.List;

/**
 * Deterministic suggested-action catalogs (no LLM call).
 * Cold-start mirrors Home quick chips; post-turn chips follow the routed intent.
 */
public final class SuggestedActions {

    private SuggestedActions() {
    }

    public static List<SuggestedAction> coldStart() {
        return List.of(
                new SuggestedAction("resume", "优化简历", "帮我看看简历有什么问题"),
                new SuggestedAction("salary", "谈涨薪", "我想跟公司谈涨薪，但不知道怎么开口"),
                new SuggestedAction("escape", "离职规划", "我在纠结要不要离职"),
                new SuggestedAction("interview", "面试准备", "帮我模拟一次面试，我在准备后端开发岗位"),
                new SuggestedAction("companion", "调整伙伴风格", "我想调整你的回答风格：更简洁、少客套"),
                new SuggestedAction("digital-employee", "创建数字员工", "我想创建一个专属数字员工")
        );
    }

    public static List<SuggestedAction> forIntent(AgentIntent intent) {
        if (intent == null) {
            return coldStart().subList(0, 4);
        }
        return switch (intent) {
            case RESUME -> List.of(
                    new SuggestedAction("resume-continue", "继续改这段", "按刚才的建议继续帮我改简历"),
                    new SuggestedAction("resume-interview", "准备面试", "基于这份简历帮我准备面试问答"),
                    new SuggestedAction("resume-export", "导出要点", "把刚才的简历修改建议整理成可执行清单"),
                    new SuggestedAction("create-resume-employee", "创建简历专员", "用简历专员模板创建一个数字员工")
            );
            case NEGOTIATION -> List.of(
                    new SuggestedAction("nego-script", "要话术", "给我一段可直接用的谈薪开场白"),
                    new SuggestedAction("nego-range", "定薪资区间", "帮我测算一个合理的目标薪资区间"),
                    new SuggestedAction("nego-counter", "模拟还价", "假如老板说预算不够，我该怎么回应"),
                    new SuggestedAction("create-nego-employee", "创建谈薪专员", "用谈薪顾问模板创建一个数字员工")
            );
            case ESCAPE -> List.of(
                    new SuggestedAction("escape-timeline", "排离职时间线", "帮我排一个稳妥的离职时间线"),
                    new SuggestedAction("escape-letter", "写离职信", "帮我写一封礼貌但坚定的离职信"),
                    new SuggestedAction("escape-handover", "交接清单", "列一份工作交接清单，避免背锅"),
                    new SuggestedAction("escape-offer", "边离职边找工作", "离职同时找下家，怎么安排节奏最安全")
            );
            case CONSULTATION -> List.of(
                    new SuggestedAction("consult-book", "确认预约", "我想预约一次职场咨询"),
                    new SuggestedAction("consult-prep", "准备咨询提纲", "帮我列预约咨询前要准备的问题"),
                    new SuggestedAction("consult-resume", "先聊简历", "预约前先帮我快速诊断一下简历"),
                    new SuggestedAction("consult-salary", "先聊薪资", "预约前先帮我理一理谈薪思路")
            );
            case DATA_QUERY -> List.of(
                    new SuggestedAction("data-manual", "手工统计法", "没有数据源时，怎么手工统计 KPI"),
                    new SuggestedAction("data-dashboard", "仪表盘建议", "给我一个职场数据仪表盘的建设步骤"),
                    new SuggestedAction("data-clarify", "澄清指标口径", "帮我把模糊的业务指标拆成可统计口径"),
                    new SuggestedAction("general-back", "换个话题", "我想聊别的职场问题")
            );
            case DIGITAL_EMPLOYEE -> List.of(
                    new SuggestedAction("de-try", "试用他", "用我刚创建的数字员工帮我处理刚才的问题"),
                    new SuggestedAction("de-persona", "改人设", "帮我优化这个数字员工的人设，让回答更专业简洁"),
                    new SuggestedAction("de-skill", "装技能", "给这个数字员工加一个常用技能建议清单"),
                    new SuggestedAction("de-list", "查看我的员工", "列出我创建的数字员工")
            );
            default -> List.of(
                    new SuggestedAction("general-resume", "转到简历", "帮我从职场角度看看简历怎么改"),
                    new SuggestedAction("general-salary", "转到谈薪", "帮我分析一下该不该谈涨薪"),
                    new SuggestedAction("general-profile", "看我的画像", "根据你记得的信息，总结一下我的职场画像"),
                    new SuggestedAction("create-employee", "创建数字员工", "我想创建一个专属数字员工")
            );
        };
    }

    public static List<SuggestedAction> forSkill(String skillName) {
        return List.of(
                new SuggestedAction("skill-again", "再跑一次", "用同样的要求再执行一次刚才的技能"),
                new SuggestedAction("skill-refine", "细化要求", "在刚才结果基础上，帮我写得更具体一点"),
                new SuggestedAction("skill-checklist", "变成清单", "把结果整理成可执行 checklist"),
                new SuggestedAction("skill-other", "换个话题", "我想聊别的职场问题")
        );
    }

    /**
     * Compact JSON array for SSE payload (no Jackson dependency at call site).
     */
    public static String toJson(List<SuggestedAction> actions) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < actions.size(); i++) {
            SuggestedAction a = actions.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(escape(a.id()))
                    .append("\",\"label\":\"").append(escape(a.label()))
                    .append("\",\"message\":\"").append(escape(a.message()))
                    .append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
