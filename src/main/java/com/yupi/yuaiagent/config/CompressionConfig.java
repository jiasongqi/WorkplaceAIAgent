package com.yupi.yuaiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对话记忆压缩配置（绑定 application.yml 中的 {@code chat.memory.compression.*}）。
 *
 * <p>集中承载记忆压缩的触发阈值与保留窗口，对应 Requirements 3.3（压缩策略可配置）/
 * 4.1（Token 阈值，默认 4000）/ 4.2（对话轮数阈值，默认 20）/ 3.2（保留最近 N 轮，默认 5）。
 *
 * <p>该配置绑定与 {@link com.yupi.yuaiagent.chatmemory.TokenCompressionStrategy}、
 * {@link com.yupi.yuaiagent.chatmemory.TurnCompressionStrategy}、
 * {@link com.yupi.yuaiagent.chatmemory.MemoryCompressor} 所读取的配置项完全一致
 * （{@code token-threshold} / {@code turn-threshold} / {@code recent-turns}），
 * 由 {@link AgentConfig} 通过 {@code @EnableConfigurationProperties} 装配为 Bean，
 * 便于集中查看与统一管理阈值语义。
 *
 * @author jsq
 */
@Data
@ConfigurationProperties(prefix = "chat.memory.compression")
public class CompressionConfig {

    /**
     * 是否启用记忆压缩，默认启用。
     */
    private boolean enabled = true;

    /**
     * Token 阈值，超过此值触发压缩，默认 4000（Requirements 4.1）。
     */
    private int tokenThreshold = 4000;

    /**
     * 对话轮数阈值，超过此值触发压缩，默认 20（Requirements 4.2）。
     */
    private int turnThreshold = 20;

    /**
     * 保留最近 N 轮对话的完整内容，默认 5（Requirements 3.2）。
     */
    private int recentTurns = 5;
}
