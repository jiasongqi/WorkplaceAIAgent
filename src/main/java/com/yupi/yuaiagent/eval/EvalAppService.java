package com.yupi.yuaiagent.eval;

import com.yupi.yuaiagent.agent.OrchestratorAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 评测应用编排层 — 串联 EvalCenter 与真实 Agent，闭环内容类评测。
 * <p>
 * 路由类套件（routing-suite）走纯规则评测（{@link EvalCenter#runEvalSuite}，零 LLM 调用，
 * 适合 CI 门禁）；内容类套件（resume-suite 等）可选择走本类的 live 评测，
 * 真实调用 {@link OrchestratorAgent#chat} 获取回答后评分。
 *
 * @author jsq
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalAppService {

    private final EvalCenter evalCenter;

    /**
     * 对 resume-suite 做真实 Agent 调用评测（每个用例一次 chat 调用，会消耗 LLM 配额）。
     */
    public EvalReport runResumeLive(OrchestratorAgent orchestratorAgent) {
        return runContentLive("resume-suite", orchestratorAgent);
    }

    /**
     * 对任意内容类套件做真实 Agent 调用评测。
     */
    public EvalReport runContentLive(String suiteId, OrchestratorAgent orchestratorAgent) {
        return evalCenter.runContentSuite(suiteId,
                input -> orchestratorAgent.chat(input, "eval-" + UUID.randomUUID()));
    }

    /**
     * 路由套件门禁（零 LLM，KeywordRouter 实跑），供 CI/发版流程调用。
     */
    public EvalReport runRoutingGate() {
        return evalCenter.runAndAssertGate("routing-suite");
    }
}
