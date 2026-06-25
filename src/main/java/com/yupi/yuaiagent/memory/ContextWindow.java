package com.yupi.yuaiagent.memory;

import java.util.Map;

/**
 * 上下文窗口 DTO — Memory Coordinator 组装的最终上下文
 *
 * <p>由各层记忆贡献内容合并而成，注入到 LLM prompt 的 SystemMessage 中。
 *
 * @param layerContributions 各层贡献的文本内容（已截断至 Token 预算内）
 * @param totalTokenCount    组装后的总 Token 估算数
 * @param formattedMessage   最终格式化的完整文本（带分层 section headers）
 */
public record ContextWindow(
        Map<MemoryLayer, String> layerContributions,
        int totalTokenCount,
        String formattedMessage
) {

    /**
     * 创建空的上下文窗口（无任何层贡献内容时使用）
     */
    public static ContextWindow empty() {
        return new ContextWindow(Map.of(), 0, "");
    }

    /**
     * 判断上下文窗口是否为空（无有效内容）
     */
    public boolean isEmpty() {
        return formattedMessage == null || formattedMessage.isBlank();
    }
}
