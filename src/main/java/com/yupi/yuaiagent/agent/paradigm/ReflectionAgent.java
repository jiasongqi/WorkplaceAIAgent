package com.yupi.yuaiagent.agent.paradigm;

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
 * Reflection Agent — generates output, evaluates it, reflects on quality, and revises.
 *
 * <p>Execution flow:</p>
 * <ol>
 *     <li><b>Generation Phase</b>: Create initial response</li>
 *     <li><b>Evaluation Phase</b>: Assess the quality and completeness</li>
 *     <li><b>Reflection Phase</b>: Identify specific improvements</li>
 *     <li><b>Revision Phase</b>: Apply improvements and generate final output</li>
 * </ol>
 *
 * <p>Best for:</p>
 * <ul>
 *     <li>Tasks requiring high quality output</li>
 *     <li>Creative writing and content generation</li>
 *     <li>Code review and optimization</li>
 *     <li>Tasks where accuracy is critical</li>
 * </ul>
 *
 * @author jsq
 * @since 2026-06-26
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ReflectionAgent extends BaseParadigmAgent {

    /** System prompt for generation phase */
    private static final String GENERATION_SYSTEM_PROMPT = """
            You are a skilled assistant. Generate a high-quality response to the user's request.
            
            Instructions:
            1. Be comprehensive and accurate
            2. Use clear and professional language
            3. Include relevant examples where appropriate
            4. Structure your response logically
            
            Focus on providing value to the user.
            """;

    /** System prompt for evaluation phase */
    private static final String EVALUATION_SYSTEM_PROMPT = """
            You are a critical evaluator. Assess the quality of the following response.
            
            Original Request: %s
            
            Generated Response:
            %s
            
            Evaluation Criteria:
            1. Accuracy: Is the information correct?
            2. Completeness: Does it fully address the request?
            3. Clarity: Is it easy to understand?
            4. Relevance: Is it focused on the user's needs?
            5. Quality: Is it well-structured and professional?
            
            Output format (strict JSON):
            {
                "overall_score": 8.5,
                "scores": {
                    "accuracy": 9,
                    "completeness": 8,
                    "clarity": 8,
                    "relevance": 9,
                    "quality": 8
                },
                "strengths": ["List of strengths"],
                "weaknesses": ["List of weaknesses"],
                "is_acceptable": true
            }
            """;

    /** System prompt for reflection phase */
    private static final String REFLECTION_SYSTEM_PROMPT = """
            You are a thoughtful reviewer. Based on the evaluation, provide specific improvement suggestions.
            
            Original Request: %s
            
            Current Response:
            %s
            
            Evaluation:
            %s
            
            Instructions:
            1. Focus on the most impactful improvements
            2. Be specific and actionable
            3. Consider both content and presentation
            4. Prioritize improvements by impact
            
            Output format:
            {
                "improvements": [
                    {
                        "priority": "high|medium|low",
                        "area": "accuracy|completeness|clarity|relevance|quality",
                        "suggestion": "Specific improvement suggestion",
                        "expected_impact": "How this will improve the response"
                    }
                ],
                "revision_strategy": "Overall strategy for revision"
            }
            """;

    /** System prompt for revision phase */
    private static final String REVISION_SYSTEM_PROMPT = """
            You are a skilled editor. Revise the response based on the reflection feedback.
            
            Original Request: %s
            
            Current Response:
            %s
            
            Reflection Feedback:
            %s
            
            Instructions:
            1. Apply all high-priority improvements
            2. Maintain the strengths of the original
            3. Ensure the revision is coherent and complete
            4. Improve overall quality and impact
            
            Provide the revised response directly, without meta-commentary.
            """;

    // Configuration
    private static final int MAX_REFLECTION_ITERATIONS = 2;
    private static final double ACCEPTABLE_SCORE = 8.0;

    // Internal state
    private String generatedResponse;
    private String evaluationResult;
    private String reflectionResult;
    private String revisedResponse;
    private int iterationCount = 0;

    /**
     * Constructor with ChatClient.
     */
    public ReflectionAgent(ChatClient chatClient) {
        super(chatClient);
    }

    @Override
    public String getParadigmName() {
        return "Reflection";
    }

    @Override
    public String getParadigmDescription() {
        return "自我批判和修正：适合需要高质量输出的任务";
    }

    /**
     * Execute the reflection paradigm.
     *
     * @return final refined response
     */
    @Override
    public String execute() {
        TraceSpan paradigmSpan = startParadigmTrace("Reflection");

        try {
            // Phase 1: Generation
            String userMessage = getMessageList().isEmpty() ? "" :
                    getMessageList().get(getMessageList().size() - 1).getText();

            generatedResponse = generateResponse(userMessage);
            if (generatedResponse == null) {
                return "生成阶段失败：无法创建初始响应";
            }

            // Iterative reflection loop
            String currentResponse = generatedResponse;
            for (iterationCount = 0; iterationCount < MAX_REFLECTION_ITERATIONS; iterationCount++) {
                log.info("[ReflectionAgent] Reflection iteration {}/{}", iterationCount + 1, MAX_REFLECTION_ITERATIONS);

                // Phase 2: Evaluation
                evaluationResult = evaluateResponse(userMessage, currentResponse);
                if (evaluationResult == null) {
                    log.warn("[ReflectionAgent] Evaluation failed, skipping reflection");
                    break;
                }

                // Check if score is acceptable
                if (isScoreAcceptable(evaluationResult)) {
                    log.info("[ReflectionAgent] Response quality acceptable, skipping further reflection");
                    break;
                }

                // Phase 3: Reflection
                reflectionResult = reflectOnResponse(userMessage, currentResponse, evaluationResult);
                if (reflectionResult == null) {
                    log.warn("[ReflectionAgent] Reflection failed, skipping revision");
                    break;
                }

                // Phase 4: Revision
                revisedResponse = reviseResponse(userMessage, currentResponse, reflectionResult);
                if (revisedResponse == null) {
                    log.warn("[ReflectionAgent] Revision failed, keeping current response");
                    break;
                }

                currentResponse = revisedResponse;
            }

            endParadigmTrace(paradigmSpan, true);
            return formatFinalResult(currentResponse);

        } catch (Exception e) {
            log.error("[ReflectionAgent] Execution failed: {}", e.getMessage(), e);
            endParadigmTrace(paradigmSpan, false);
            return "执行错误：" + e.getMessage();
        }
    }

    /**
     * Phase 1: Generate initial response.
     */
    private String generateResponse(String userMessage) {
        TraceSpan generationSpan = startPhaseTrace("Generation");

        try {
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(GENERATION_SYSTEM_PROMPT),
                            new UserMessage(userMessage))
            );

            ChatResponse response = getChatClient().prompt(prompt).call().chatResponse();
            String result = response.getResult().getOutput().getText();

            log.info("[ReflectionAgent] Generated initial response ({} chars)", result.length());
            endPhaseTrace(generationSpan, true);
            return result;

        } catch (Exception e) {
            log.error("[ReflectionAgent] Generation failed: {}", e.getMessage());
            endPhaseTrace(generationSpan, false);
            return null;
        }
    }

    /**
     * Phase 2: Evaluate the response quality.
     */
    private String evaluateResponse(String userMessage, String response) {
        TraceSpan evaluationSpan = startPhaseTrace("Evaluation");

        try {
            String systemPrompt = String.format(EVALUATION_SYSTEM_PROMPT, userMessage, response);
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(systemPrompt),
                            new UserMessage("Evaluate this response."))
            );

            ChatResponse chatResponse = getChatClient().prompt(prompt).call().chatResponse();
            String evaluation = chatResponse.getResult().getOutput().getText();

            log.info("[ReflectionAgent] Evaluation completed");
            endPhaseTrace(evaluationSpan, true);
            return evaluation;

        } catch (Exception e) {
            log.error("[ReflectionAgent] Evaluation failed: {}", e.getMessage());
            endPhaseTrace(evaluationSpan, false);
            return null;
        }
    }

    /**
     * Phase 3: Reflect on improvements.
     */
    private String reflectOnResponse(String userMessage, String response, String evaluation) {
        TraceSpan reflectionSpan = startPhaseTrace("Reflection");

        try {
            String systemPrompt = String.format(REFLECTION_SYSTEM_PROMPT, userMessage, response, evaluation);
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(systemPrompt),
                            new UserMessage("Provide improvement suggestions."))
            );

            ChatResponse chatResponse = getChatClient().prompt(prompt).call().chatResponse();
            String reflection = chatResponse.getResult().getOutput().getText();

            log.info("[ReflectionAgent] Reflection completed");
            endPhaseTrace(reflectionSpan, true);
            return reflection;

        } catch (Exception e) {
            log.error("[ReflectionAgent] Reflection failed: {}", e.getMessage());
            endPhaseTrace(reflectionSpan, false);
            return null;
        }
    }

    /**
     * Phase 4: Revise the response.
     */
    private String reviseResponse(String userMessage, String response, String reflection) {
        TraceSpan revisionSpan = startPhaseTrace("Revision");

        try {
            String systemPrompt = String.format(REVISION_SYSTEM_PROMPT, userMessage, response, reflection);
            Prompt prompt = new Prompt(
                    List.of(new SystemMessage(systemPrompt),
                            new UserMessage("Revise and improve the response."))
            );

            ChatResponse chatResponse = getChatClient().prompt(prompt).call().chatResponse();
            String revised = chatResponse.getResult().getOutput().getText();

            log.info("[ReflectionAgent] Revision completed ({} chars)", revised.length());
            endPhaseTrace(revisionSpan, true);
            return revised;

        } catch (Exception e) {
            log.error("[ReflectionAgent] Revision failed: {}", e.getMessage());
            endPhaseTrace(revisionSpan, false);
            return null;
        }
    }

    // ─── Helper Methods ────────────────────────────────────────────────

    /**
     * Check if evaluation score is acceptable.
     */
    private boolean isScoreAcceptable(String evaluation) {
        try {
            // Simple score extraction - in production, use structured output
            if (evaluation.contains("\"overall_score\"")) {
                int start = evaluation.indexOf("\"overall_score\":") + 15;
                int end = evaluation.indexOf(",", start);
                if (end == -1) end = evaluation.indexOf("}", start);
                String scoreStr = evaluation.substring(start, end).trim();
                double score = Double.parseDouble(scoreStr);
                return score >= ACCEPTABLE_SCORE;
            }
        } catch (Exception e) {
            log.debug("[ReflectionAgent] Score parsing failed: {}", e.getMessage());
        }
        return false; // Assume not acceptable if parsing fails
    }

    /**
     * Format final result with metadata.
     */
    private String formatFinalResult(String finalResponse) {
        StringBuilder result = new StringBuilder();

        // Add reflection metadata
        result.append("## 🔄 反思优化结果\n\n");
        result.append(String.format("- 反思迭代次数: %d\n", iterationCount + 1));
        result.append(String.format("- 最终响应长度: %d 字符\n\n", finalResponse.length()));

        // Add the final response
        result.append("### 最终响应\n\n");
        result.append(finalResponse);

        // Add evaluation summary if available
        if (evaluationResult != null) {
            result.append("\n\n### 📊 质量评估\n\n");
            result.append(extractScoreSummary(evaluationResult));
        }

        return result.toString();
    }

    /**
     * Extract score summary from evaluation.
     */
    private String extractScoreSummary(String evaluation) {
        // Simple extraction - in production, use structured output
        try {
            if (evaluation.contains("\"overall_score\"")) {
                int start = evaluation.indexOf("\"overall_score\":") + 15;
                int end = evaluation.indexOf(",", start);
                if (end == -1) end = evaluation.indexOf("}", start);
                String scoreStr = evaluation.substring(start, end).trim();
                return "综合评分: " + scoreStr + "/10";
            }
        } catch (Exception e) {
            log.debug("[ReflectionAgent] Score extraction failed: {}", e.getMessage());
        }
        return "评估数据解析中...";
    }
}
