package com.yupi.yuaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
public class WebSearchTool {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = """
            Search the public web (Baidu via SearchAPI) for real-time facts, news, salary/market data, \
            company updates, or events after the model's knowledge cutoff.
            WHEN TO USE: user asks for current events, live market figures, or external references not in the knowledge base.
            DO NOT USE: general career common-sense; internal uploaded docs (use searchKnowledgeBase / RAG); \
            when you already have a concrete URL (use scrapeWebPage or startScrapeWebPage instead).
            RETURNS: up to 5 results with title, snippet, and link. Read-only and safe to retry on timeout.""")
    public String searchWeb(
            @ToolParam(description = "Concise search query keywords in Chinese or English; avoid pasting long documents") String query) {
        int maxRetries = 3;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Map<String, Object> paramMap = new HashMap<>();
                paramMap.put("q", query);
                paramMap.put("api_key", apiKey);
                paramMap.put("engine", "baidu");
                String response = HttpUtil.get(SEARCH_API_URL, paramMap, 10_000);
                JSONObject jsonObject = JSONUtil.parseObj(response);
                JSONArray organicResults = jsonObject.getJSONArray("organic_results");
                int limit = Math.min(5, organicResults.size());
                String result = organicResults.subList(0, limit).stream().map(obj -> {
                    JSONObject item = (JSONObject) obj;
                    return String.format("标题：%s\n摘要：%s\n链接：%s",
                            item.getStr("title", ""),
                            item.getStr("snippet", ""),
                            item.getStr("link", ""));
                }).collect(Collectors.joining("\n---\n"));
                return result;
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return "Error searching Baidu after " + maxRetries + " attempts: "
                + (lastException != null ? lastException.getMessage() : "unknown");
    }
}
