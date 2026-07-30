package com.yupi.yuaiagent.artifact.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交付物查询条件封装
 * <p>
 * 用于 Artifact_Shelf 的多条件查询。全部字段可选，{@code null} 表示该条件不参与约束；
 * 多个非空条件之间为 AND 语义。
 *
 * @author jsq
 */
@Data
@Builder
public class ArtifactQuery {

    /**
     * 归属用户；为 null 时不按 userId 约束
     */
    private String userId;

    /**
     * 归属会话；为 null 时不按 chatId 约束
     */
    private String chatId;

    /**
     * 交付物类型；为 null 时不按 type 约束
     */
    private String type;

    /**
     * 作用域；为 null 时不按 scope 约束
     */
    private ArtifactScope scope;

    /**
     * 状态；为 null 时不按 status 约束
     */
    private ArtifactStatus status;

    /**
     * 目标 Agent；由数据库过滤 targetAgents。
     */
    private String targetAgent;

    /**
     * 是否可复用。
     */
    private Boolean reusable;

    /**
     * 仅返回在该时刻尚未过期的数据。
     */
    private LocalDateTime activeAt;

    /**
     * 数据库查询上限。
     */
    private Integer limit;
}
