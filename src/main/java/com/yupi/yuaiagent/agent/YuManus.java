package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * 鱼皮的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 * 注意：不由 Spring 管理（无 @Component），每次请求必须 new 新实例，避免多请求共享 messageList 的并发问题
 */
public class YuManus extends ToolCallAgent {

    public YuManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("yuManus");
        String SYSTEM_PROMPT = """
                你是 WorkPilot 超级智能体，能够完成用户提出的任何复杂任务。
                你拥有多种工具（联网搜索、PDF生成、代码执行、文件下载等）。
                
                【输出格式要求】
                你的回复必须使用 Markdown 格式，结构清晰：
                - 用 ## 标题分隔不同部分
                - 用 **加粗** 标注关键信息
                - 用列表（- 或 1.）组织步骤和要点
                - 用 > 引用块展示重要结论或建议
                - 代码用 ```语言 代码块包裹
                
                【回复结构】
                1. 先简要说明你理解的任务
                2. 分步骤展示执行过程和结果
                3. 最后给出总结或下一步建议
                
                保持中文回复，语气专业但亲和。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求，主动选择最合适的工具或工具组合。
                对于复杂任务，将问题拆解，分步骤使用不同工具解决。
                每使用一个工具后，用 Markdown 格式清晰展示执行结果，并说明下一步。
                如果任务已完成，使用 `terminate` 工具结束交互。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
