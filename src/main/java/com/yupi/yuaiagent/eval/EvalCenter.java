package com.yupi.yuaiagent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 评测中心 — Agent 质量评测框架，支持回归测试和发版评估。
 * <p>
 * 评测流程：加载用例 → 调用 Agent → 评分 → 生成报告 → 检测回归。
 * 未来扩展：ReplayDatasetBuilder 从 Trace/Session 自动抽样生成评测用例。
 *
 * @author jsq
 */
@Slf4j
@Service
public class EvalCenter {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, List<EvalCase>> suites = new HashMap<>();
    private final List<EvalReport> reports = Collections.synchronizedList(new ArrayList<>());

    public EvalCenter() {
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
                    String suiteName = resource.getFilename().replace(".yaml", "");
                    suites.put(suiteName, cases);
                    log.debug("加载评测套件: {} ({} cases)", suiteName, cases.size());
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
     * 运行评测套件（V1：框架就绪，实际执行需接入 Agent）
     */
    public EvalReport runEvalSuite(String suiteId) {
        List<EvalCase> cases = suites.get(suiteId);
        if (cases == null || cases.isEmpty()) {
            log.warn("评测套件不存在或为空: {}", suiteId);
            return null;
        }

        log.info("[EvalCenter] 开始评测: suite={}, cases={}", suiteId, cases.size());

        List<EvalReport.CaseResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            EvalReport.CaseResult result = EvalReport.CaseResult.builder()
                    .caseId(evalCase.getCaseId())
                    .input(evalCase.getInput())
                    .actualOutput("[待执行 - 需要接入 Agent]")
                    .score(0.0)
                    .passed(false)
                    .feedback("评测框架已就绪，等待 Agent 接入后自动执行")
                    .build();
            results.add(result);
        }

        EvalReport report = EvalReport.builder()
                .reportId(UUID.randomUUID().toString())
                .suiteName(suiteId)
                .agentType(suiteId)
                .totalCases(cases.size())
                .passedCases(0)
                .failedCases(cases.size())
                .overallScore(0.0)
                .passRate(0.0)
                .caseResults(results)
                .executedAt(LocalDateTime.now())
                .build();

        reports.add(report);
        return report;
    }

    public Optional<EvalReport> getLatestReport(String suiteId) {
        return reports.stream()
                .filter(r -> suiteId.equals(r.getSuiteName()))
                .max(Comparator.comparing(EvalReport::getExecutedAt));
    }

    public Set<String> getSuiteNames() {
        return suites.keySet();
    }

    public List<EvalReport> getAllReports() {
        return Collections.unmodifiableList(reports);
    }
}
