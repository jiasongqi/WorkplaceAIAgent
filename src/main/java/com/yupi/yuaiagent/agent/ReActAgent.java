package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.budget.TokenBudgetManager;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    // Guard component — optional, non-invasive token budget management (Req 4.3, 4.8)
    // 注意：ReActAgent 通过 new 创建（非 Spring Bean），不能使用 @Autowired
    // 由子类或外部调用者通过 setter 注入
    private TokenBudgetManager tokenBudgetManager;

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            // --- Guard: TokenBudgetManager — check budget BEFORE think() (Req 4.3) ---
            if (tokenBudgetManager != null) {
                try {
                    tokenBudgetManager.checkBudget(getMessageList());
                } catch (Exception e) {
                    log.warn("[ReActAgent] token budget check failed, skipping: {}", e.getMessage());
                }
            }

            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // LLM 判断无需调用工具 → 任务完成，终止循环
                // 修复：不设置 FINISHED 会导致 BaseAgent 循环继续浪费 LLM 调用
                setState(com.yupi.yuaiagent.agent.model.AgentState.FINISHED);
                return "思考完成 - 无需行动";
            }
            // 再行动
            return act();
        } catch (Exception e) {
            // 记录异常日志（使用 SLF4J 而非 printStackTrace）
            log.error("[ReActAgent] step execution failed", e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

}
