package com.yupi.yuaiagent.eval;

import com.yupi.yuaiagent.agent.AgentIntent;
import com.yupi.yuaiagent.nlu.NluPipeline;
import com.yupi.yuaiagent.skill.SkillDefinition;
import com.yupi.yuaiagent.skill.SkillRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 路由准确率评测
 * 
 * 评测维度：
 * 1. 路由准确率 — 消息是否被分发到正确的 Agent
 * 2. 快速路径覆盖率 — 哪些用例走了快速路径（无 LLM 调用）
 * 3. 响应时间 — 每个用例的端到端延迟
 *
 * 运行方式：mvn test -Dtest=AgentRoutingEvalTest -pl .
 */
@SpringBootTest
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentRoutingEvalTest {

    @Resource
    private NluPipeline nluPipeline;

    @Resource
    private SkillRegistry skillRegistry;

    // ========== 测试数据集 ==========

    record EvalCase(String message, AgentIntent expectedIntent, String category) {}

    static List<EvalCase> buildTestCases() {
        List<EvalCase> cases = new ArrayList<>();

        // === RESUME Agent ===
        cases.add(new EvalCase("帮我优化简历", AgentIntent.RESUME, "简历"));
        cases.add(new EvalCase("简历投了很多没回音怎么办", AgentIntent.RESUME, "简历"));
        cases.add(new EvalCase("我的简历需要修改一下项目经历部分", AgentIntent.RESUME, "简历"));
        cases.add(new EvalCase("写简历有什么技巧", AgentIntent.RESUME, "简历"));
        cases.add(new EvalCase("投递简历总是被拒", AgentIntent.RESUME, "简历"));

        // === NEGOTIATION Agent ===
        cases.add(new EvalCase("我想跟老板谈涨薪", AgentIntent.NEGOTIATION, "谈薪"));
        cases.add(new EvalCase("月薪2万想要3万怎么谈", AgentIntent.NEGOTIATION, "谈薪"));
        cases.add(new EvalCase("加薪被拒绝了怎么办", AgentIntent.NEGOTIATION, "谈薪"));
        cases.add(new EvalCase("跳槽薪资怎么报价比较合适", AgentIntent.NEGOTIATION, "谈薪"));
        cases.add(new EvalCase("期望薪资该怎么说", AgentIntent.NEGOTIATION, "谈薪"));

        // === ESCAPE Agent ===
        cases.add(new EvalCase("我想离职但不知道怎么开口", AgentIntent.ESCAPE, "离职"));
        cases.add(new EvalCase("被裁员了怎么办", AgentIntent.ESCAPE, "离职"));
        cases.add(new EvalCase("辞职信怎么写", AgentIntent.ESCAPE, "离职"));
        cases.add(new EvalCase("竞业限制怎么应对", AgentIntent.ESCAPE, "离职"));
        cases.add(new EvalCase("离职交接要注意什么", AgentIntent.ESCAPE, "离职"));

        // === GENERAL Agent ===
        cases.add(new EvalCase("我要发财", AgentIntent.GENERAL, "通用"));
        cases.add(new EvalCase("你好", AgentIntent.GENERAL, "通用"));
        cases.add(new EvalCase("今天天气好吗", AgentIntent.GENERAL, "通用"));
        cases.add(new EvalCase("工作压力好大怎么缓解", AgentIntent.GENERAL, "通用"));
        cases.add(new EvalCase("同事关系不好怎么处理", AgentIntent.GENERAL, "通用"));
        cases.add(new EvalCase("职场中如何提升自己", AgentIntent.GENERAL, "通用"));
        cases.add(new EvalCase("35岁程序员出路在哪", AgentIntent.GENERAL, "通用"));

        // === 边界用例（容易误判） ===
        cases.add(new EvalCase("面试要怎么准备", AgentIntent.GENERAL, "边界"));
        cases.add(new EvalCase("年终奖太少了", AgentIntent.GENERAL, "边界"));
        cases.add(new EvalCase("领导PUA我", AgentIntent.GENERAL, "边界"));

        return cases;
    }

    // ========== 评测执行 ==========

    @Test
    @Order(1)
    @DisplayName("路由准确率评测")
    void evalRoutingAccuracy() {
        List<EvalCase> cases = buildTestCases();
        AtomicInteger correct = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(0);
        Map<String, int[]> categoryStats = new LinkedHashMap<>(); // category -> [correct, total]

        List<String> failures = new ArrayList<>();

        for (EvalCase evalCase : cases) {
            total.incrementAndGet();
            String chatId = UUID.randomUUID().toString();

            long start = System.currentTimeMillis();
            AgentIntent actualIntent = resolveRoutedIntent(evalCase.message(), chatId);
            long elapsed = System.currentTimeMillis() - start;

            boolean pass = actualIntent == evalCase.expectedIntent();
            if (pass) {
                correct.incrementAndGet();
            } else {
                failures.add(String.format("  ✗ \"%s\" → 期望 %s, 实际 %s (%.1fs)",
                    evalCase.message(), evalCase.expectedIntent(), actualIntent, elapsed / 1000.0));
            }

            // Per-category stats
            categoryStats.computeIfAbsent(evalCase.category(), k -> new int[]{0, 0});
            int[] stats = categoryStats.get(evalCase.category());
            stats[1]++;
            if (pass) stats[0]++;

            log.info("[{}] \"{}\" → {} (期望: {}) {}ms {}",
                pass ? "✓" : "✗", evalCase.message(), actualIntent,
                evalCase.expectedIntent(), elapsed, pass ? "" : "← WRONG");
        }

        // 打印评测报告
        log.info("\n========== 评测报告 ==========");
        log.info("总准确率: {}/{} = {:.1f}%", correct.get(), total.get(),
            correct.get() * 100.0 / total.get());
        log.info("\n分类准确率:");
        for (var entry : categoryStats.entrySet()) {
            int[] s = entry.getValue();
            log.info("  {} : {}/{} = {:.1f}%", entry.getKey(), s[0], s[1], s[0] * 100.0 / s[1]);
        }
        if (!failures.isEmpty()) {
            log.info("\n失败用例:");
            failures.forEach(log::info);
        }
        log.info("==============================\n");

        // 断言：准确率至少 80%
        Assertions.assertTrue(correct.get() * 100.0 / total.get() >= 80.0,
            "路由准确率低于 80%，当前: " + (correct.get() * 100.0 / total.get()) + "%");
    }

    /**
     * 与 {@link com.yupi.yuaiagent.agent.OrchestratorAgent#chat} 同步路径一致：先技能，再 NLU。
     */
    private AgentIntent resolveRoutedIntent(String message, String chatId) {
        List<SkillDefinition> matchedSkills = skillRegistry.findByIntent(message);
        if (!matchedSkills.isEmpty()) {
            return intentFromSkill(matchedSkills.get(0).getName());
        }

        NluPipeline.NluResult nluResult = nluPipeline.process(message, chatId);
        if (nluResult.isNeedsClarification()) {
            return nluResult.toAgentIntent();
        }
        AgentIntent intent = nluResult.toAgentIntent();
        if (intent == AgentIntent.DATA_QUERY) {
            return AgentIntent.GENERAL;
        }
        return intent;
    }

    private AgentIntent intentFromSkill(String skillName) {
        return switch (skillName) {
            case "resume-review" -> AgentIntent.RESUME;
            case "salary-research" -> AgentIntent.NEGOTIATION;
            case "resignation-letter" -> AgentIntent.ESCAPE;
            default -> AgentIntent.GENERAL;
        };
    }
}
