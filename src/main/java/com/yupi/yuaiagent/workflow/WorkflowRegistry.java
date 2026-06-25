package com.yupi.yuaiagent.workflow;

import com.yupi.yuaiagent.agent.task.FailurePolicy;
import com.yupi.yuaiagent.budget.TokenBudget;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all available workflow templates.
 * V2: hardcoded. V3: loaded from config.
 *
 * @author jsq
 */
@Component
public class WorkflowRegistry {

    private final Map<String, WorkflowTemplate> templates = new HashMap<>();

    @PostConstruct
    public void init() {
        // Job change preparation
        templates.put("JOB_CHANGE", new WorkflowTemplate(
            "JOB_CHANGE", "v1", "跳槽准备",
            "resume.optimize",  // routePrefix
            List.of("跳槽", "换工作", "offer", "涨薪", "离职"),
            List.of(
                PlanStep.of("RESUME", "简历优化"),
                PlanStep.of("NEGOTIATION", "薪资分析"),
                PlanStep.of("GENERAL", "面试准备")
            ),
            FailurePolicy.RETRY_THEN_SKIP,
            new TokenBudget(8000, 4000, 12000),
            false
        ));

        // Interview preparation
        templates.put("INTERVIEW", new WorkflowTemplate(
            "INTERVIEW", "v1", "面试准备",
            "resume.interview",  // routePrefix
            List.of("面试", "八股文", "自我介绍", "模拟面试"),
            List.of(
                PlanStep.of("RESUME", "简历优化"),
                PlanStep.of("GENERAL", "面试辅导")
            ),
            FailurePolicy.RETRY_THEN_SKIP,
            new TokenBudget(6000, 3000, 9000),
            false
        ));

        // Consultation booking
        templates.put("CONSULTATION", new WorkflowTemplate(
            "CONSULTATION", "v1", "咨询预约",
            "consultation.book",  // routePrefix
            List.of("预约", "咨询", "约时间"),
            List.of(PlanStep.of("CONSULTATION", "预约咨询")),
            FailurePolicy.FAIL_FAST,
            new TokenBudget(4000, 2000, 6000),
            false
        ));

        // Generic career (fallback)
        templates.put("GENERIC_CAREER", new WorkflowTemplate(
            "GENERIC_CAREER", "v1", "职场通用",
            "career.general",  // routePrefix
            List.of(),
            List.of(PlanStep.of("GENERAL", "职场顾问")),
            FailurePolicy.RETRY_THEN_FAIL,
            new TokenBudget(4000, 2000, 6000),
            false
        ));

        // Data query
        templates.put("DATA_QUERY", new WorkflowTemplate(
            "DATA_QUERY", "v1", "数据查询",
            "advertiser.query",  // routePrefix — matches advertiser.query.*
            List.of("数据", "查询", "指标", "报表"),
            List.of(PlanStep.of("DATA_QUERY", "数据查询")),
            FailurePolicy.RETRY_THEN_SKIP,
            new TokenBudget(4000, 2000, 6000),
            false
        ));
    }

    public WorkflowTemplate get(String id) { return templates.get(id); }
    public Collection<WorkflowTemplate> getAll() { return templates.values(); }
}
