package com.yupi.yuaiagent.agent.paradigm;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.agent.model.AgentState;
import com.yupi.yuaiagent.trace.model.TraceSpan;
import com.yupi.yuaiagent.trace.model.TraceStepType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan-and-Solve Agent — generates a plan first, then executes step by step.
 *
 * <p>Execution flow:</p>
 * <ol>
 *     <li><b>Planning Phase</b>: Analyze the task and generate a structured plan</li>
 *     <li><b>Execution Phase</b>: Execute each step in the plan sequentially</li>
 *     <li><b>Verification Phase</b>: Verify the final result</li>
 * </ol>
 *
 * <p>Best for:</p>
 * <ul>
 *     <li>Complex multi-step tasks</li>
 *     <li>Research and analysis tasks</li>
 *     <li>Tasks requiring structured approach</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class PlanAndSolveAgent extends BaseParadigmAgent {

    /** System prompt for planning phase */
    private static final String PLANNING_SYSTEM_PROMPT = """
            You are a strategic planning assistant. Your task is to analyze the user's request and create a clear, actionable plan.
            
            Instructions:
            1. Break down the task into 3-7 concrete steps
            2. Each step should be specific and achievable
            3. Consider dependencies between steps
            4. Include verification steps where appropriate
            
            Output format (strict JSON):
            {
                "analysis": "Brief analysis of the task",
                "steps": [
                    {"id": 1, "action": "Specific action to take", "expected_output": "What this step produces"},
                    ...
                ],
                "estimated_complexity": "low|medium|high"
            }
            """;

    /** System prompt for execution phase */
    private static final String EXECUTION_SYSTEM_PROMPT = """
            You are a task executor. Execute the current step of the plan precisely.
            
            Current Plan Progress:
            %s
            
            Current Step: %d of %d
            Step Action: %s
            
            Instructions:
            1. Focus only on the current step
            2. Provide clear, actionable output
            3. If you need information, state what's needed
            4. Mark step as COMPLETE when done
            """;

    /** System prompt for verification phase */
    private static final String VERIFICATION_SYSTEM_PROMPT = """
            You are a quality assurance assistant. Review the execution results and verify completeness.
            
            Original Task: %s
            
            Plan:
            %s
            
            Execution Results:
            %s
            
            Instructions:
            1. Check if all steps were completed
            2. Verify the results meet the original task requirements
            3. Identify any gaps or issues
            4. Provide a final summary
            
            Output format:
            {
                "status": "complete|partial|failed",
                "summary": "Final summary of results",
                "issues": ["List of any issues found"],
                "recommendations": ["Suggestions for improvement"]
            }
            """;

    // Internal state
    private String planJson;
    private List<PlanStep> planSteps;
    private int currentStepIndex = 0;
    private List<String> stepResults;
    private Phase currentPhase = Phase.PLANNING;

    /**
     * Constructor with ChatClient.
     */
    public PlanAndSolveAgent(ChatClient chatClient) {
        super(chatClient);
        this.stepResults = new ArrayList<>();
    }

    @Override
    public String getParadigmName() {
        return "Plan-and-Solve";
    }

    @Override
    public String getParadigmDescription() {
        return "先规划后执行：适合复杂多步骤任务";
    }

    /**
     * Execute the plan-and-solve paradigm.
     *
     * @return execution result
     */
    @Override
    public String execute() {
        TraceSpan paradigmSpan = startParadigmTrace("Plan-and-Solve");

        try {
            // Phase 1: Planning
            currentPhase = Phase.PLANNING;
            String planResult = generatePlan();
            if (planResult == null) {
                return "规划阶段失败：无法生成计划";
            }

            // Phase 2: Execution
            currentPhase = Phase.EXECUTION;
            String executionResult = executePlan();
            if (executionResult == null) {
                return "执行阶段失败：计划执行中断";
            }

            // Phase 3: Verification
            currentPhase = Phase.VERIFICATION;
            String verificationResult = verifyResults();

            endParadigmTrace(paradigmSpan, true);
            return formatFinalResult(planResult, executionResult, verificationResult);

        } catch (Exception e) {
            log.error("[PlanAndSolveAgent] Execution failed at phase {}: {}", currentPhase, e.getMessage(), e);
            endParadigmTrace(paradigmSpan, false);
            return "执行错误：" + e.getMessage();
        }
    }

    /**
     * Phase 1: Generate a structured plan.
     */
    private String generatePlan() {
        TraceSpan planningSpan = startPhaseTrace("Planning");

        try {
            // Build planning prompt
            String userMessage = getMessageList().isEmpty() ? "" :
                    getMessageList().get(getMessageList().size() - 1).getText();

            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(PLANNING_SYSTEM_PROMPT),
                            new UserMessage(userMessage))
            );

            // Call LLM for planning
            ChatResponse response = getChatClient().prompt(prompt).call().chatResponse();
            AssistantMessage output = response.getResult().getOutput();
            String planResponse = output.getText();

            // Parse plan (simplified - in production, use structured output)
            this.planJson = planResponse;
            this.planSteps = parsePlan(planResponse);

            if (planSteps == null || planSteps.isEmpty()) {
                log.warn("[PlanAndSolveAgent] Failed to parse plan, using single-step fallback");
                this.planSteps = List.of(new PlanStep(1, userMessage, "Direct execution"));
            }

            log.info("[PlanAndSolveAgent] Generated plan with {} steps", planSteps.size());
            endPhaseTrace(planningSpan, true);
            return planResponse;

        } catch (Exception e) {
            log.error("[PlanAndSolveAgent] Planning failed: {}", e.getMessage());
            endPhaseTrace(planningSpan, false);
            return null;
        }
    }

    /**
     * Phase 2: Execute each step in the plan.
     */
    private String executePlan() {
        TraceSpan executionSpan = startPhaseTrace("Execution");

        try {
            StringBuilder results = new StringBuilder();

            for (int i = 0; i < planSteps.size(); i++) {
                currentStepIndex = i;
                PlanStep step = planSteps.get(i);

                log.info("[PlanAndSolveAgent] Executing step {}/{}: {}", i + 1, planSteps.size(), step.action());

                // Build execution prompt
                String progress = buildProgressSummary();
                String systemPrompt = String.format(EXECUTION_SYSTEM_PROMPT,
                        progress, i + 1, planSteps.size(), step.action());

                Prompt prompt = new Prompt(
                        List.of(new SystemMessage(systemPrompt),
                                new UserMessage("Execute this step and provide the result."))
                );

                // Execute step
                ChatResponse response = getChatClient().prompt(prompt).call().chatResponse();
                String stepResult = response.getResult().getOutput().getText();

                // Record result
                stepResults.add(stepResult);
                results.append("Step ").append(i + 1).append(": ").append(step.action()).append("\n");
                results.append("Result: ").append(stepResult).append("\n\n");

                // Update trace
                addStepToTrace(step.action(), stepResult);
            }

            endPhaseTrace(executionSpan, true);
            return results.toString();

        } catch (Exception e) {
            log.error("[PlanAndSolveAgent] Execution failed: {}", e.getMessage());
            endPhaseTrace(executionSpan, false);
            return null;
        }
    }

    /**
     * Phase 3: Verify the results.
     */
    private String verifyResults() {
        TraceSpan verificationSpan = startPhaseTrace("Verification");

        try {
            String userMessage = getMessageList().isEmpty() ? "" :
                    getMessageList().get(getMessageList().size() - 1).getText();

            String planSummary = buildPlanSummary();
            String resultsSummary = buildResultsSummary();

            String systemPrompt = String.format(VERIFICATION_SYSTEM_PROMPT,
                    userMessage, planSummary, resultsSummary);

            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(systemPrompt),
                            new UserMessage("Verify the results and provide final assessment."))
            );

            ChatResponse response = getChatClient().prompt(prompt).call().chatResponse();
            String verification = response.getResult().getOutput().getText();

            log.info("[PlanAndSolveAgent] Verification completed");
            endPhaseTrace(verificationSpan, true);
            return verification;

        } catch (Exception e) {
            log.error("[PlanAndSolveAgent] Verification failed: {}", e.getMessage());
            endPhaseTrace(verificationSpan, false);
            return "验证阶段跳过：" + e.getMessage();
        }
    }

    // ─── Helper Methods ────────────────────────────────────────────────

    /**
     * Parse plan from LLM response using Jackson for robust JSON parsing.
     */
    @SuppressWarnings("unchecked")
    private List<PlanStep> parsePlan(String planResponse) {
        List<PlanStep> steps = new ArrayList<>();

        try {
            // Extract JSON from response (LLM may add extra text)
            String json = extractJson(planResponse);
            
            // Parse with Jackson
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> planMap = mapper.readValue(json, Map.class);
            
            // Extract steps array
            List<Map<String, Object>> stepsList = (List<Map<String, Object>>) planMap.get("steps");
            if (stepsList != null) {
                int stepId = 1;
                for (Map<String, Object> stepMap : stepsList) {
                    String action = (String) stepMap.getOrDefault("action", "Execute step " + stepId);
                    String expectedOutput = (String) stepMap.getOrDefault("expected_output", "");
                    steps.add(new PlanStep(stepId++, action, expectedOutput));
                }
            }
        } catch (Exception e) {
            log.warn("[PlanAndSolveAgent] JSON parsing failed, using fallback: {}", e.getMessage());
            // Fallback: try line-by-line parsing
            steps = parsePlanFallback(planResponse);
        }

        // Final fallback: if still empty, create generic steps
        if (steps.isEmpty()) {
            steps.add(new PlanStep(1, "Analyze the task requirements", "Analysis complete"));
            steps.add(new PlanStep(2, "Execute the main task", "Task executed"));
            steps.add(new PlanStep(3, "Verify and summarize results", "Verification complete"));
        }

        log.info("[PlanAndSolveAgent] Parsed {} steps from plan", steps.size());
        return steps;
    }

    /**
     * Extract JSON object from text that may contain extra content.
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * Fallback parser for non-JSON responses.
     */
    private List<PlanStep> parsePlanFallback(String planResponse) {
        List<PlanStep> steps = new ArrayList<>();
        String[] lines = planResponse.split("\n");
        int stepId = 1;

        for (String line : lines) {
            line = line.trim();
            // Match numbered list items: "1. action" or "1) action"
            if (line.matches("^\\d+[.)]\\s+.*")) {
                String action = line.replaceFirst("^\\d+[.)]\\s+", "");
                steps.add(new PlanStep(stepId++, action, "Output from step " + (stepId - 1)));
            }
        }
        return steps;
    }
                steps.add(new PlanStep(3, "Verify and summarize results", "Verification complete"));
            }

        } catch (Exception e) {
            log.warn("[PlanAndSolveAgent] Plan parsing failed: {}", e.getMessage());
        }

        return steps;
    }

    /**
     * Build progress summary for execution context.
     */
    private String buildProgressSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Completed steps:\n");

        for (int i = 0; i < stepResults.size(); i++) {
            PlanStep step = planSteps.get(i);
            summary.append(String.format("- Step %d: %s → %s\n",
                    i + 1, step.action(),
                    stepResults.get(i).substring(0, Math.min(100, stepResults.get(i).length()))));
        }

        return summary.toString();
    }

    /**
     * Build plan summary for verification.
     */
    private String buildPlanSummary() {
        StringBuilder summary = new StringBuilder();
        for (PlanStep step : planSteps) {
            summary.append(String.format("%d. %s\n", step.id(), step.action()));
        }
        return summary.toString();
    }

    /**
     * Build results summary for verification.
     */
    private String buildResultsSummary() {
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < stepResults.size(); i++) {
            summary.append(String.format("Step %d: %s\n", i + 1, stepResults.get(i)));
        }
        return summary.toString();
    }

    /**
     * Format final result.
     */
    private String formatFinalResult(String plan, String execution, String verification) {
        return String.format("""
                ## 📋 执行计划
                
                %s
                
                ## 🔧 执行结果
                
                %s
                
                ## ✅ 验证结果
                
                %s
                """, planJson, execution, verification);
    }

    // ─── Inner Classes ─────────────────────────────────────────────────

    /**
     * Plan step record.
     */
    public record PlanStep(int id, String action, String expectedOutput) {}

    /**
     * Execution phases.
     */
    public enum Phase {
        PLANNING,
        EXECUTION,
        VERIFICATION
    }
}
