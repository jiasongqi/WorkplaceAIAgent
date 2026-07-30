package com.yupi.yuaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.budget.TokenBudgetManager;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

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
            if (getRunBudget() != null && getRunBudget().isExhausted()) {
                return "Token 预算已用尽，停止本步执行";
            }
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
                setState(com.yupi.yuaiagent.agent.model.AgentState.FINISHED);
                String reply = lastAssistantText();
                // Ch4 Gotcha 5.2: block "I have done it" without Tool Output
                String claimWarn = com.yupi.yuaiagent.agent.loop.CompletionClaimGuard
                        .checkUnsupportedClaim(getMessageList(), reply);
                if (claimWarn != null) {
                    getMessageList().add(new org.springframework.ai.chat.messages.UserMessage(claimWarn));
                    log.warn("[ReActAgent] completion claim without tool success");
                    return (StrUtil.isNotBlank(reply) ? reply + "\n\n" : "")
                            + "> 注意：系统未检测到成功的工具回执，请勿将计划视为已完成。";
                }
                return StrUtil.isNotBlank(reply) ? reply : "思考完成 - 无需行动";
            }
            // 再行动
            return act();
        } catch (Exception e) {
            // 记录异常日志（使用 SLF4J 而非 printStackTrace）
            log.error("[ReActAgent] step execution failed", e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    private String lastAssistantText() {
        List<Message> messages = getMessageList();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        Message last = messages.get(messages.size() - 1);
        if (last instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        }
        return null;
    }

}
