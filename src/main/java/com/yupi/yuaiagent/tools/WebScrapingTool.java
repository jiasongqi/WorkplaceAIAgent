package com.yupi.yuaiagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {

    @Tool(description = "Scrape the main text content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document document = Jsoup.connect(url)
                    .timeout(10_000)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();
            // 只返回正文 text，去除 HTML 标签、script、style 等噪音
            return document.body().text();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
