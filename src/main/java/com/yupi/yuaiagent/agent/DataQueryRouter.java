package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.nlu.RouteHint;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Data query handler — Phase 1: formats extracted slots as structured response.
 * Phase 2: connects to MCP data tools for actual data retrieval.
 *
 * <p>NOT an LLM agent — no model call. Takes RouteHint and produces Flux response directly.
 *
 * @author jsq
 */
@Slf4j
public class DataQueryRouter {

    /**
     * Handle data query intent — format NLU-extracted slots into a readable response.
     */
    public Flux<String> chatStream(RouteHint routeHint, String message, String chatId) {
        String response = formatSlotsResponse(routeHint);
        log.info("[DataQuery] entity={}, metric={}, timeRange={}",
            routeHint.entity(), routeHint.metric(), routeHint.timeRange());
        return Flux.just(response);
    }

    private String formatSlotsResponse(RouteHint hint) {
        StringBuilder sb = new StringBuilder();

        if (hint.entity() != null) {
            sb.append("为您查询 ").append(hint.entity());

            if (hint.metric() != null) {
                sb.append(" 的 ").append(hint.metric());
            } else {
                sb.append(" 的核心指标");
            }

            if (hint.timeRange() != null) {
                sb.append("（").append(formatTimeRange(hint.timeRange())).append("）");
            }

            sb.append("：\n\n");
            sb.append("📊 数据查询功能正在建设中，目前可以为您提供以下支持：\n");
            sb.append("1. 数据趋势分析建议\n");
            sb.append("2. 指标异常排查思路\n");
            sb.append("3. 竞品对比分析框架\n\n");
            sb.append("请问您想从哪个角度开始？");

        } else {
            sb.append("请问您想查询哪个主体的数据？（如：腾讯资方、百度资方）");
        }

        return sb.toString();
    }

    private String formatTimeRange(String timeRange) {
        return switch (timeRange) {
            case "7d" -> "近7天";
            case "1d" -> "昨天";
            case "2d" -> "前天";
            case "this_month" -> "本月";
            case "last_week" -> "上周";
            default -> timeRange;
        };
    }
}
