package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.guard.UrlSafetyGuard;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具 — SSRF 防护委托 {@link UrlSafetyGuard}
 */
public class WebScrapingTool {

    @Tool(description = "Scrape the main text content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        if (!UrlSafetyGuard.isSafeUrl(url)) {
            return UrlSafetyGuard.rejectMessage();
        }
        int maxRetries = 3;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Document document = Jsoup.connect(url)
                        .timeout(10_000)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .followRedirects(true)
                        .maxBodySize(2 * 1024 * 1024)
                        .get();
                // Re-check final URL after redirects (basic DNS rebinding / redirect SSRF mitigation)
                String finalUrl = document.location();
                if (finalUrl != null && !UrlSafetyGuard.isSafeUrl(finalUrl)) {
                    return UrlSafetyGuard.rejectMessage() + " (blocked after redirect)";
                }
                return document.body() != null ? document.body().text() : "";
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
        return "Error scraping web page after " + maxRetries + " attempts: "
                + (lastException != null ? lastException.getMessage() : "unknown");
    }
}
