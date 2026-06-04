package com.yupi.yuaiagent.agent.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据分析报告结构化内容
 * <p>
 * 数据分析师产出的结构化报告，序列化为 JSON 后作为 {@code Artifact.content} 承载。
 *
 * @author jsq
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReport {

    /**
     * 分析摘要
     */
    private String summary;

    /**
     * 关键发现
     */
    private List<String> keyFindings;

    /**
     * 指标（可选）
     */
    private Map<String, Object> metrics;

    /**
     * 建议（可选）
     */
    private List<String> recommendations;
}
