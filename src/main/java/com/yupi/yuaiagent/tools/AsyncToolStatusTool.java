package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.tools.async.AsyncToolTaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Shared poll tool for Submit-Poll async tool tasks.
 */
public class AsyncToolStatusTool {

    private final AsyncToolTaskService asyncToolTaskService;

    public AsyncToolStatusTool(AsyncToolTaskService asyncToolTaskService) {
        this.asyncToolTaskService = asyncToolTaskService;
    }

    @Tool(description = """
            Check status of an asynchronous tool task started by startScrapeWebPage / startDownloadResource / startGeneratePDF.
            WHEN TO USE: you previously received a taskId and need RUNNING / COMPLETED / FAILED status.
            RETURNS: status message; when COMPLETED includes the full tool result.
            Read-only; safe to poll repeatedly.""")
    public String checkAsyncToolTask(
            @ToolParam(description = "taskId returned by a start* async tool") String taskId) {
        if (asyncToolTaskService == null) {
            return "Async tool service unavailable";
        }
        return asyncToolTaskService.statusMessage(taskId);
    }
}
