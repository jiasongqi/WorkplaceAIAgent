package com.yupi.yuaiagent.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yupi.yuaiagent.metrics.AgentExecutionMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * 评测中心 — 加载 YAML 套件 → 执行 → 评分 → 回归门禁。
 * <p>
 * V2：路由套件用 KeywordRouter 实跑；内容套件用启发式评分（可扩展 LLM_JUDGE）。
 * 发版门禁：passRate &lt; 历史最优或 &lt; 配置阈值 → regression=true。
 */
@Slf4j
@Service
public class EvalCenter {

    public static final double DEFAULT_PASS_RATE_GATE = 0.80;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, List<EvalCase>> suites = new HashMap<>();
    private final List<EvalReport> reports = Collections.synchronizedList(new ArrayList<>());
    private final EvalScorer evalScorer;
    private final AgentExecutionMetrics agentExecutionMetrics;
    private final double passRateGate;

    public EvalCenter(EvalScorer evalScorer,
                      AgentExecutionMetrics agentExecutionMetrics) {
        this.evalScorer = evalScorer;
        this.agentExecutionMetrics = agentExecutionMetrics;
        this.passRateGate = DEFAULT_PASS_RATE_GATE;
        loadEvalSuites();
    }

    private void loadEvalSuites() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:eval/*.yaml");
            for (Resource resource : resources) {
                try {
                    List<EvalCase> cases = yamlMapper.readValue(
                            resource.getInputStream(),
                            new TypeReference<List<EvalCase>>() {});
                    String suiteName = Objects.requireNonNull(resource.getFilename()).replace(".yaml", "");
                    suites.put(suiteName, cases);
                    log.info("加载评测套件: {} ({} cases)", suiteName, cases.size());
                } catch (Exception e) {
                    log.warn("加载评测文件失败: {}", resource.getFilename(), e);
                }
            }
            log.info("评测中心初始化完成，共加载 {} 个评测套件", suites.size());
        } catch (IOException e) {
            log.warn("扫描评测目录失败（可能是目录不存在）: {}", e.getMessage());
        }
    }

    /**
     * 运行评测套件（真实执行 + 评分 + 回归检测）。
     */
    public EvalReport runEvalSuite(String suiteId) {
        List<EvalCase> cases = suites.get(suiteId);
        if (cases == null || cases.isEmpty()) {
            log.warn("评测套件不存在或为空: {}", suiteId);
            return null;
        }

        log.info("[EvalCenter] 开始评测: suite={}, cases={}", suiteId, cases.size());
        boolean routingSuite = suiteId.toLowerCase().contains("routing")
                || cases.stream().anyMatch(c ->
                "ROUTING".equalsIgnoreCase(c.getScoringRule())
                        || StringUtils.hasText(c.getExpectedIntent()));

        List<EvalReport.CaseResult> results = new ArrayList<>();
        double scoreSum = 0.0;
        int passed = 0;

        for (EvalCase evalCase : cases) {
            long start = System.currentTimeMillis();
            EvalScorer.ScoreResult scored;
            if (routingSuite || "ROUTING".equalsIgnoreCase(evalCase.getScoringRule())) {
                scored = evalScorer.scoreRouting(evalCase);
            } else {
                // Content suite without live agent: mark as not-run (score=null→0, passed=false)
                // CI should use routing-suite; content needs scoreLiveCase() or shadow traffic.
                scored = new EvalScorer.ScoreResult(
                        0.0,
                        "[NOT_RUN — provide live agent output via scoreLiveCase]",
                        "content suite requires live agent; routing-suite is the automated gate");
            }

            double threshold = evalCase.getPassThreshold() > 0 ? evalCase.getPassThreshold() : 0.8;
            boolean ok = scored.score() >= threshold;
            if (ok) passed++;
            scoreSum += scored.score();

            results.add(EvalReport.CaseResult.builder()
                    .caseId(evalCase.getCaseId())
                    .input(evalCase.getInput())
                    .actualOutput(scored.actualOutput())
                    .score(scored.score())
                    .passed(ok)
                    .feedback(scored.feedback())
                    .build());

            if (routingSuite) {
                agentExecutionMetrics.recordExecutionStart("EVAL_ROUTING");
                agentExecutionMetrics.recordExecutionEnd(
                        "EVAL_ROUTING", System.currentTimeMillis() - start, 0, 0, 1, ok);
            }
        }

        int total = cases.size();
        double passRate = total == 0 ? 0.0 : (double) passed / total;
        double overall = total == 0 ? 0.0 : scoreSum / total;

        boolean belowGate = routingSuite && passRate < passRateGate;
        boolean regression = routingSuite && detectRegression(suiteId, passRate);

        EvalReport report = EvalReport.builder()
                .reportId(UUID.randomUUID().toString())
                .suiteName(suiteId)
                .agentType(routingSuite ? "ROUTING" : suiteId)
                .totalCases(total)
                .passedCases(passed)
                .failedCases(total - passed)
                .overallScore(overall)
                .passRate(passRate)
                .regression(regression || belowGate)
                .caseResults(results)
                .executedAt(LocalDateTime.now())
                .build();

        reports.add(report);
        log.info("[EvalCenter] suite={} passRate={} regression={} gate={}",
                suiteId, String.format("%.2f", passRate), report.isRegression(), passRateGate);
        return report;
    }

    /**
     * Score a content case with a real agent answer (for integration / shadow eval).
     */
    public EvalReport.CaseResult scoreLiveCase(EvalCase evalCase, String actualOutput) {
        EvalScorer.ScoreResult scored = evalScorer.scoreContent(evalCase, actualOutput);
        double threshold = evalCase.getPassThreshold() > 0 ? evalCase.getPassThreshold() : 0.5;
        return EvalReport.CaseResult.builder()
                .caseId(evalCase.getCaseId())
                .input(evalCase.getInput())
                .actualOutput(scored.actualOutput())
                .score(scored.score())
                .passed(scored.score() >= threshold)
                .feedback(scored.feedback())
                .build();
    }

    /**
     * 运行内容评测套件，使用真实 Agent 调用（而非占位 NOT_RUN 评分）。
     * <p>
     * 对套件内每个用例调用 {@code agentFn.apply(input)} 获取真实回答，再用
     * {@link #scoreLiveCase} 评分。适用于 resume-suite 等内容类套件的"闭环"评测。
     */
    public EvalReport runContentSuite(String suiteId, Function<String, String> agentFn) {
        List<EvalCase> cases = suites.get(suiteId);
        if (cases == null || cases.isEmpty()) {
            log.warn("评测套件不存在或为空: {}", suiteId);
            return null;
        }

        log.info("[EvalCenter] 开始内容评测（live agent）: suite={}, cases={}", suiteId, cases.size());

        List<EvalReport.CaseResult> results = new ArrayList<>();
        double scoreSum = 0.0;
        int passed = 0;

        for (EvalCase evalCase : cases) {
            long start = System.currentTimeMillis();
            String actualOutput;
            try {
                actualOutput = agentFn.apply(evalCase.getInput());
            } catch (Exception e) {
                log.warn("[EvalCenter] live case {} 执行失败: {}", evalCase.getCaseId(), e.getMessage());
                actualOutput = "";
            }
            EvalReport.CaseResult result = scoreLiveCase(evalCase, actualOutput);
            results.add(result);
            if (result.isPassed()) passed++;
            scoreSum += result.getScore();

            agentExecutionMetrics.recordExecutionStart("EVAL_CONTENT");
            agentExecutionMetrics.recordExecutionEnd(
                    "EVAL_CONTENT", System.currentTimeMillis() - start, 0, 0, 1, result.isPassed());
        }

        int total = cases.size();
        double passRate = total == 0 ? 0.0 : (double) passed / total;
        double overall = total == 0 ? 0.0 : scoreSum / total;
        boolean regression = detectRegression(suiteId, passRate);

        EvalReport report = EvalReport.builder()
                .reportId(UUID.randomUUID().toString())
                .suiteName(suiteId)
                .agentType("CONTENT_LIVE")
                .totalCases(total)
                .passedCases(passed)
                .failedCases(total - passed)
                .overallScore(overall)
                .passRate(passRate)
                .regression(regression || passRate < passRateGate)
                .caseResults(results)
                .executedAt(LocalDateTime.now())
                .build();

        reports.add(report);
        log.info("[EvalCenter] content-live suite={} passRate={} regression={}",
                suiteId, String.format("%.2f", passRate), report.isRegression());
        return report;
    }

    private boolean detectRegression(String suiteId, double newPassRate) {
        Optional<EvalReport> prev = getLatestReport(suiteId);
        if (prev.isEmpty()) {
            return false;
        }
        // Regression if drop > 5 percentage points vs previous run
        return newPassRate + 0.05 < prev.get().getPassRate();
    }

    public Optional<EvalReport> getLatestReport(String suiteId) {
        return reports.stream()
                .filter(r -> suiteId.equals(r.getSuiteName()))
                .max(Comparator.comparing(EvalReport::getExecutedAt));
    }

    public Set<String> getSuiteNames() {
        return suites.keySet();
    }

    public List<EvalCase> getSuiteCases(String suiteId) {
        return suites.getOrDefault(suiteId, List.of());
    }

    public List<EvalReport> getAllReports() {
        return Collections.unmodifiableList(reports);
    }

    /**
     * CI gate helper: run suite and throw if regression / below gate.
     */
    public EvalReport runAndAssertGate(String suiteId) {
        EvalReport report = runEvalSuite(suiteId);
        if (report == null) {
            throw new IllegalStateException("Eval suite not found: " + suiteId);
        }
        if (report.isRegression() || report.getPassRate() < passRateGate) {
            throw new AssertionError("Eval gate failed: suite=" + suiteId
                    + " passRate=" + report.getPassRate()
                    + " regression=" + report.isRegression()
                    + " gate=" + passRateGate);
        }
        return report;
    }
}
