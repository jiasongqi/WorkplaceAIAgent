package com.yupi.yuaiagent.agent.data;

/**
 * 数据员工执行上下文
 * <p>
 * 承载数据员工加工所需的输入信息：归属用户、归属会话、分析来源，
 * 以及不同来源对应的取数据依据（对话记忆 agent 类型 / 上传文档内容）。
 *
 * @param userId          归属用户
 * @param chatId          归属会话
 * @param source          分析来源（对话历史 / 上传文档）
 * @param memoryAgentType 用于从 ChatMemoryManager 取哪个 agent 的记忆
 * @param documentContent 上传文档内容（source 为 UPLOADED_DOCUMENT 时使用）
 * @author jsq
 */
public record ProductionContext(
        String userId,
        String chatId,
        AnalysisSource source,
        String memoryAgentType,
        String documentContent) {
}
