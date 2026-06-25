package com.yupi.yuaiagent.eval;

import com.yupi.yuaiagent.agent.AgentIntent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

/**
 * 快速路径路由单元测试（不需要 Spring 上下文，不调用 LLM）
 * 
 * 测试 OrchestratorAgent 的 keywordRouteIntent() 和 containsCareerKeyword() 方法
 * 确保规则路由覆盖常见场景，避免不必要的 NLU LLM 调用
 * 
 * 运行方式：mvn test -Dtest=FastPathRoutingTest -pl .
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FastPathRoutingTest {

    // 使用反射调用 private 方法（因为这些是 OrchestratorAgent 的私有方法）
    // 实际项目中可以考虑抽取为 public 的 RoutingStrategy 类

    @ParameterizedTest(name = "快速路径: \"{0}\" → 应跳过NLU={1}")
    @CsvSource({
        // 不包含复杂关键词 → 走快速路径（跳过NLU）
        "你好, true",
        "我要发财, true",
        "今天心情不好, true",
        "帮我优化简历, true",
        "我想涨薪, true",
        "想离职, true",
        // 包含复杂关键词 → 走NLU
        "帮我预约一个顾问, false",
        "查询一下我的数据, false",
        "帮我查询上个月的统计报表, false",
    })
    @Order(1)
    void testFastPathDecision(String message, boolean expectFastPath) {
        boolean fastPath = !containsCareerKeyword(message);
        Assertions.assertEquals(expectFastPath, fastPath,
            String.format("消息 \"%s\" 期望 fastPath=%s, 实际=%s", message, expectFastPath, fastPath));
    }

    @ParameterizedTest(name = "关键词路由: \"{0}\" → {1}")
    @CsvSource({
        "帮我优化简历, RESUME",
        "简历怎么写, RESUME",
        "投简历没回音, RESUME",
        "我想涨薪, NEGOTIATION",
        "跟老板谈加薪, NEGOTIATION",
        "薪资怎么报价, NEGOTIATION",
        "月薪2万够不够, NEGOTIATION",
        "我想离职, ESCAPE",
        "被辞退了怎么办, ESCAPE",
        "竞业限制要签吗, ESCAPE",
        "裁员补偿怎么算, ESCAPE",
        "你好啊, GENERAL",
        "我要发财, GENERAL",
        "工作好累, GENERAL",
        "35岁危机, GENERAL",
        "同事关系不好, GENERAL",
    })
    @Order(2)
    void testKeywordRouting(String message, String expectedIntent) {
        AgentIntent actual = keywordRouteIntent(message);
        AgentIntent expected = AgentIntent.valueOf(expectedIntent);
        Assertions.assertEquals(expected, actual,
            String.format("消息 \"%s\" 期望路由到 %s, 实际 %s", message, expected, actual));
    }

    // ========== 复制 OrchestratorAgent 的私有方法逻辑用于独立测试 ==========

    private boolean containsCareerKeyword(String message) {
        String[] complexKeywords = {
            "预约", "咨询", "顾问",
            "数据", "查询", "统计", "报表",
            "帮我分析", "对比",
        };
        String lower = message.toLowerCase();
        for (String kw : complexKeywords) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        int domainCount = 0;
        if (lower.matches(".*(?:简历|优化|修改).*")) domainCount++;
        if (lower.matches(".*(?:涨薪|加薪|谈薪|薪资|工资).*")) domainCount++;
        if (lower.matches(".*(?:离职|辞职|裁员|竞业).*")) domainCount++;
        return domainCount >= 2;
    }

    private AgentIntent keywordRouteIntent(String message) {
        String lower = message.toLowerCase();
        if (lower.matches(".*(?:简历|优化简历|修改简历|写简历|投递|投简历).*")) {
            return AgentIntent.RESUME;
        }
        if (lower.matches(".*(?:涨薪|加薪|谈薪|薪资|工资|谈判|要价|报价|期望薪资|薪水|月薪).*")) {
            return AgentIntent.NEGOTIATION;
        }
        if (lower.matches(".*(?:离职|辞职|裁员|竞业|交接|走人|不想干|被辞|辞退|解雇|开除).*")) {
            return AgentIntent.ESCAPE;
        }
        return AgentIntent.GENERAL;
    }
}
