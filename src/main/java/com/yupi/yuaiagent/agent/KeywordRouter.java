package com.yupi.yuaiagent.agent;

/**
 * Rule-based keyword router — maps user messages to AgentIntent
 * without any LLM call. Covers ~80% of single-intent queries instantly.
 *
 * <p>Extracted from OrchestratorAgent to reduce god-class complexity.
 *
 * @author jsq
 */
public final class KeywordRouter {

    private KeywordRouter() {}

    /**
     * Whether the message spans 2+ career domains and needs full NLU
     * (e.g. 「改简历顺便谈薪」).
     */
    public static boolean hasMultiDomainConflict(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        int domainCount = 0;
        if (lower.matches(".*(?:简历|优化|修改|投递).*")) domainCount++;
        if (lower.matches(".*(?:涨薪|加薪|谈薪|薪资|工资|谈判).*")) domainCount++;
        if (lower.matches(".*(?:离职|辞职|裁员|竞业).*")) domainCount++;
        if (lower.matches(".*(?:预约|约时间|可约).*")) domainCount++;
        return domainCount >= 2;
    }

    /**
     * Queries that still need the NLU LLM for slot/entity extraction
     * (not covered by {@link #keywordRouteIntent}).
     */
    public static boolean needsSlotExtraction(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        String[] slotKeywords = {
            "数据", "查询", "统计", "报表", "kpi", "roi",
            "帮我分析", "对比"
        };
        for (String kw : slotKeywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return lower.matches(".*和.+比.*");
    }

    /**
     * Quick keyword check: message may need full NLU (slots / ambiguity).
     * Prefer calling {@link #keywordRouteIntent} first for clear single intents.
     */
    public static boolean containsCareerKeyword(String message) {
        if (needsSlotExtraction(message) || hasMultiDomainConflict(message)) {
            return true;
        }
        String[] complexKeywords = {
            "预约", "咨询", "顾问",
        };
        String lower = message == null ? "" : message.toLowerCase();
        for (String kw : complexKeywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rule-based keyword routing — maps message to the most likely AgentIntent
     * without any LLM call. Returns null if no clear match (caller should use NLU).
     */
    public static AgentIntent keywordRouteIntent(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String lower = message.toLowerCase();

        // RESUME agent
        if (lower.matches(".*(?:简历|优化简历|修改简历|写简历|投递|投简历).*")) {
            return AgentIntent.RESUME;
        }
        // NEGOTIATION agent
        if (lower.matches(".*(?:涨薪|加薪|谈薪|薪资|工资|谈判|要价|报价|期望薪资|薪水|月薪).*")) {
            return AgentIntent.NEGOTIATION;
        }
        // ESCAPE agent
        if (lower.matches(".*(?:离职|辞职|裁员|竞业|交接|走人|不想干|被辞|辞退|解雇|开除).*")) {
            return AgentIntent.ESCAPE;
        }
        // CONSULTATION：明确预约 / 问可约服务或课程（目录由 ConsultationAgent 返回）
        if (lower.matches(".*(?:预约|约时间|约个顾问|预约咨询|预约专家|可预约|预约的课程).*")
                || lower.matches(".*(?:有什么|有哪些).*(?:预约|可约|课程).*")
                || lower.matches(".*咨询预约.*")) {
            return AgentIntent.CONSULTATION;
        }
        // Interview → GENERAL
        if (lower.matches(".*(?:面试|准备面试|模拟面试|面经|笔试).*")) {
            return AgentIntent.GENERAL;
        }

        return null; // No clear match — caller should use NLU Pipeline
    }
}
