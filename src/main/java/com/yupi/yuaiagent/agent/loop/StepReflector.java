package com.yupi.yuaiagent.agent.loop;

import com.yupi.yuaiagent.guard.ToolResultClassifier;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/**
 * Lightweight step Reflect after non-NORMAL tool results (Ch4 O-T-A-R Reflect).
 * Template-based — no extra LLM call.
 */
public final class StepReflector {

    private StepReflector() {
    }

    public static void reflectIfNeeded(ToolResultClassifier.ResultGrade grade,
                                       String observation,
                                       List<Message> messageList) {
        if (grade == null || grade == ToolResultClassifier.ResultGrade.NORMAL || messageList == null) {
            return;
        }
        String mono = switch (grade) {
            case TIMEOUT -> "Reflect: 工具超时。不应重复相同慢调用；可改用 start* 异步工具或缩小范围。";
            case EMPTY -> "Reflect: 观测为空，说明当前方向可能错误。下一步必须更换关键词/工具，禁止同参重试。";
            case GARBAGE -> "Reflect: 观测不可用（拦截页/垃圾内容）。更换来源，不要基于无效观测下结论。";
            default -> "Reflect: 上一步观测异常，先修正策略再行动。";
        };
        String snippet = observation == null ? "" :
                observation.substring(0, Math.min(180, observation.length()));
        messageList.add(new UserMessage("[Step Reflect] " + mono
                + (snippet.isBlank() ? "" : " 观测摘录: " + snippet)));
    }
}
