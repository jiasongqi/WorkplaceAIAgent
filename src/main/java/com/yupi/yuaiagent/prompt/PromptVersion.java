package com.yupi.yuaiagent.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Prompt 版本 — 支持 Prompt 版本化、灰度发布和 A/B 测试。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptVersion {
    private String promptKey;
    private int version;
    /** DRAFT / ACTIVE / CANARY / DEPRECATED */
    private String status;
    /** 模板内容，支持 {variable} 占位符 */
    private String template;
    private String author;
    private String description;
    /** A/B 测试流量分配百分比 */
    private int trafficPercent;
    private Map<String, String> parameters;
    private LocalDateTime createdAt;
}
