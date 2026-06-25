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
     * Quick keyword check to decide if NLU LLM call is needed.
     * Returns true if message clearly relates to complex career topics
     * requiring full NLU (multi-intent / ambiguous / slot-filling).
     */
    public static boolean containsCareerKeyword(String message) {
        String[] complexKeywords = {
            "预约", "咨询", "顾问",           // appointment needs slot filling
            "数据", "查询", "统计", "报表",   // data query needs entity extraction
            "帮我分析", "对比", "和.*比",     // comparative analysis
        };
        String lower = message.toLowerCase();
        for (String kw : complexKeywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        // Multi-intent detection: message contains 2+ different domain keywords
        int domainCount = 0;
        if (lower.matches(".*(?:简历|优化|修改).*")) domainCount++;
        if (lower.matches(".*(?:涨薪|加薪|谈薪|薪资|工资).*")) domainCount++;
        if (lower.matches(".*(?:离职|辞职|裁员|竞业).*")) domainCount++;
        return domainCount >= 2;
    }

    /**
     * Rule-based keyword routing — maps message to the most likely AgentIntent
     * without any LLM call. Returns null if no clear match (caller should use NLU).
     */
    public static AgentIntent keywordRouteIntent(String message) {
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
        // CONSULTATION agent
        if (lower.matches(".*(?:预约|咨询|约时间|顾问).*")) {
            return AgentIntent.CONSULTATION;
        }
        // Interview → GENERAL
        if (lower.matches(".*(?:面试|准备面试|模拟面试|面经|笔试).*")) {
            return AgentIntent.GENERAL;
        }

        return null; // No clear match — caller should use NLU Pipeline
    }
}
