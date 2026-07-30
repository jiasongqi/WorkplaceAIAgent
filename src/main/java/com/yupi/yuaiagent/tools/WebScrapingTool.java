package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.guard.UrlSafetyGuard;
import com.yupi.yuaiagent.tools.async.AsyncToolTaskService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具 — SSRF 防护委托 {@link UrlSafetyGuard}
 */
public class WebScrapingTool {

    private final AsyncToolTaskService asyncToolTaskService;

    public WebScrapingTool() {
        this(null);
    }

    public WebScrapingTool(AsyncToolTaskService asyncToolTaskService) {
        this.asyncToolTaskService = asyncToolTaskService;
    }

    @Tool(description = """
            Fetch and extract the main visible text of a public web page by URL (sync, may take several seconds).
            WHEN TO USE: you already have a concrete http(s) URL and need page body text.
            DO NOT USE: open-ended discovery (use searchWeb first); internal knowledge-base docs (use searchKnowledgeBase); \
            expected runtime over ~25s (use startScrapeWebPage then checkAsyncToolTask).
            RETURNS: plain text body (may be long — host will sanitize/truncate). Read-only; safe to retry on timeout.""")
    public String scrapeWebPage(@ToolParam(description = "Full http(s) URL of the page to scrape") String url) {
        return doScrape(url);
    }

    @Tool(description = """
            Start an asynchronous web scrape for potentially slow pages. Returns a taskId immediately.
            WHEN TO USE: page may be slow / large; or previous scrapeWebPage timed out.
            After calling, use checkAsyncToolTask(taskId) on later turns until COMPLETED or FAILED.
            DO NOT USE for tiny fast pages — prefer scrapeWebPage.""")
    public String startScrapeWebPage(@ToolParam(description = "Full http(s) URL of the page to scrape") String url) {
        if (asyncToolTaskService == null) {
            return doScrape(url);
        }
        if (!UrlSafetyGuard.isSafeUrl(url)) {
            return UrlSafetyGuard.rejectMessage();
        }
        String taskId = asyncToolTaskService.submit("scrapeWebPage", "scrape " + url, () -> doScrape(url));
        return AsyncToolTaskService.submittedMessage(taskId, "scrapeWebPage");
    }

    String doScrape(String url) {
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
