package com.yupi.yuaiagent.artifact.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交付物实体（共享交付物货架的核心数据模型）
 * <p>
 * 由某个 Agent 产出并放入 Artifact_Shelf，下游 Agent 可按需查询、取用并标记消费。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Artifact {

    /**
     * 全局唯一 ID（未指定时由仓库生成 UUID）
     */
    private String artifactId;

    /**
     * 归属用户（USER_PROFILE 作用域的归属键）
     */
    private String userId;

    /**
     * 归属会话（TASK 作用域的归属键）
     */
    private String chatId;

    /**
     * 交付物类型，如 DATA_ANALYSIS_REPORT
     */
    private String type;

    /**
     * 生产者标识名（数据员工名称）
     */
    private String producer;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容：结构化 JSON 字符串或纯文本，二者皆以 String 承载
     */
    private String content;

    /**
     * 用于召回注入的短摘要，避免把完整正文塞入上下文。
     */
    private String summary;

    /**
     * 是否允许跨轮次重复召回。
     */
    private boolean reusable;

    /**
     * 允许消费该交付物的目标 Agent。
     */
    @Builder.Default
    private List<String> targetAgents = List.of();

    /**
     * 发布幂等键。
     */
    private String dedupKey;

    /**
     * 结构化内容 schema 版本。
     */
    private Integer schemaVersion;

    /**
     * 过期时间；为空表示长期有效。
     */
    private LocalDateTime expiresAt;

    /**
     * 生产该交付物的 Trace。
     */
    private String sourceTraceId;

    /**
     * 状态
     */
    private ArtifactStatus status;

    /**
     * 作用域
     */
    private ArtifactScope scope;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
