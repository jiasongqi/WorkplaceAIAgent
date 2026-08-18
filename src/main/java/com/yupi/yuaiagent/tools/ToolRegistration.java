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

    public static final String ALL_TOOLS_BEAN_NAME = "allTools";

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Bean(name = ALL_TOOLS_BEAN_NAME)
    public ToolCallback[] allTools(SandboxFactory sandboxFactory,
                                   HumanApprovalService humanApprovalService,
                                   ToolIdempotencyStore idempotencyStore,
                                   FileHandleStore fileHandleStore,
                                   AsyncToolTaskService asyncToolTaskService,
                                   RagTool ragTool,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   com.yupi.yuaiagent.config.PlatformProperties platformProperties,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false)
                                   com.yupi.yuaiagent.tools.transform.ToolTransformerChain transformerChain) {
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
        ToolCallback[] callbacks = ToolCallbacks.from(
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
        boolean wrap = platformProperties != null
                && platformProperties.getToolTransformer() != null
                && platformProperties.getToolTransformer().isEnabled()
                && transformerChain != null;
        if (!wrap) {
            return callbacks;
        }
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrapped[i] = new com.yupi.yuaiagent.tools.transform.TransformingToolCallback(
                    callbacks[i], transformerChain);
        }
        return wrapped;
    }
}
