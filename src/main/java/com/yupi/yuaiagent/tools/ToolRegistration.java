package com.yupi.yuaiagent.tools;

import com.yupi.yuaiagent.hitl.HumanApprovalService;
import com.yupi.yuaiagent.rag.RagTool;
import com.yupi.yuaiagent.sandbox.SandboxFactory;
import com.yupi.yuaiagent.tools.async.AsyncToolTaskService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools(SandboxFactory sandboxFactory,
                                   HumanApprovalService humanApprovalService,
                                   ToolIdempotencyStore idempotencyStore,
                                   FileHandleStore fileHandleStore,
                                   AsyncToolTaskService asyncToolTaskService,
                                   RagTool ragTool) {
        FileOperationTool fileOperationTool = new FileOperationTool(
                humanApprovalService, idempotencyStore, fileHandleStore);
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool(asyncToolTaskService);
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool(
                idempotencyStore, asyncToolTaskService);
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool(
                sandboxFactory, humanApprovalService, idempotencyStore);
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool(
                idempotencyStore, asyncToolTaskService);
        AsyncToolStatusTool asyncToolStatusTool = new AsyncToolStatusTool(asyncToolTaskService);
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                asyncToolStatusTool,
                terminateTool,
                ragTool
        );
    }
}
