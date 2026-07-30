package com.yupi.yuaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 终止工具（作用是让自主规划智能体能够合理地中断）
 */
public class TerminateTool {

    @Tool(description = """
            Terminate the agent loop when the user request is fully met OR you cannot proceed further.
            WHEN TO USE: all required steps are done, or blockers require stopping and reporting to the user.
            DO NOT USE: mid-task while more tools are still needed; do not invent other terminate tool names.
            If no tool is needed, answer the user directly instead of calling tools.""")
    public String doTerminate() {
        return "任务结束";
    }
}