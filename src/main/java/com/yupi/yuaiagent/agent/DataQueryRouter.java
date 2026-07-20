package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.nlu.RouteHint;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Data query handler — currently NOT connected to any real business data source.
 * Formats NLU-extracted slots into an honest fallback response that recommends
 * career-general alternatives (manual stats, query criteria, dashboard steps)
 * instead of pretending to have queried real data.
 *
 * <p>NOT an LLM agent — no model call. Takes RouteHint and produces Flux response directly.
 *
 * <p>In the main chat path, {@code OrchestratorAgent} maps DATA_QUERY → GENERAL with an
 * injected note (see {@code OrchestratorAgent#DATA_QUERY_FALLBACK_NOTE}) so the specialist
 * itself states the limitation to the user. This class remains as a standalone fallback
 * for callers that invoke DATA_QUERY routing directly.
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
            sb.append("当前未接入真实业务数据源，我无法为您提供已查到的真实数据，不能替您编造数字。");
            sb.append("以职场顾问的角度，我可以帮您：\n");
            sb.append("1. 设计手工统计方法：如何从现有报表/表格中整理出这个指标\n");
            sb.append("2. 明确问数口径：这个指标的定义、统计周期、对比维度应该如何界定\n");
            sb.append("3. 仪表盘建设步骤：如果要长期跟踪，可以怎样搭建一个简单看板\n\n");
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
