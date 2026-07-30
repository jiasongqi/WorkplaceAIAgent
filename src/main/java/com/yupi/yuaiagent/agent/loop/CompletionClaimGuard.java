package com.yupi.yuaiagent.agent.loop;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Guards against "I have done it" hallucination (Ch4 Gotcha 5.2):
 * model claims a side-effect completed without a matching successful Tool Output.
 */
public final class CompletionClaimGuard {

    private static final Pattern CLAIM = Pattern.compile(
            "(已(经)?(发送|写入|下载|生成|删除|执行|创建|提交)|邮件已发|文件已写|PDF已生成|命令已执行)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SUCCESS_TOOL = Pattern.compile(
            "(success|成功|written successfully|downloaded successfully|PDF 生成成功|Resource downloaded)",
            Pattern.CASE_INSENSITIVE);

    private CompletionClaimGuard() {
    }

    /**
     * @return warning text if claim is unsupported; null if OK
     */
    public static String checkUnsupportedClaim(List<Message> history, String assistantText) {
        if (assistantText == null || assistantText.isBlank()) {
            return null;
        }
        if (!CLAIM.matcher(assistantText).find()) {
            return null;
        }
        if (hasSuccessfulToolOutput(history)) {
            return null;
        }
        return "[Guard] 你声称已完成写入/发送/下载等副作用，但本轮对话中没有对应的成功 Tool Output。"
                + "请勿把「计划调用工具」当成「已经完成」。只有系统注入的 Tool Output 才能证明动作已执行；"
                + "若仍需完成，请先发起真实工具调用。";
    }

    public static boolean hasSuccessfulToolOutput(List<Message> history) {
        if (history == null) {
            return false;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if (m instanceof ToolResponseMessage trm) {
                for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                    String data = r.responseData();
                    if (data == null) {
                        continue;
                    }
                    if (SUCCESS_TOOL.matcher(data).find()
                            && !data.toLowerCase(Locale.ROOT).contains("error")
                            && !data.contains("失败")
                            && !data.contains("pending-approval")) {
                        return true;
                    }
                }
                return false; // found tool responses but none successful in last batch
            }
            if (m instanceof AssistantMessage) {
                // keep scanning older tool messages
                continue;
            }
        }
        return false;
    }
}
