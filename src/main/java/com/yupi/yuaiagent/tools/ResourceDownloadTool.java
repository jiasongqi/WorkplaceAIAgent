package com.yupi.yuaiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.yupi.yuaiagent.constant.FileConstant;
import com.yupi.yuaiagent.guard.UrlSafetyGuard;
import com.yupi.yuaiagent.tools.async.AsyncToolTaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 资源下载工具（路径穿越校验 + SSRF 防护 + 幂等）
 */
public class ResourceDownloadTool {

    private final ToolIdempotencyStore idempotencyStore;
    private final AsyncToolTaskService asyncToolTaskService;

    public ResourceDownloadTool() {
        this(null, null);
    }

    public ResourceDownloadTool(ToolIdempotencyStore idempotencyStore, AsyncToolTaskService asyncToolTaskService) {
        this.idempotencyStore = idempotencyStore;
        this.asyncToolTaskService = asyncToolTaskService;
    }

    @Tool(description = """
            Download a remote resource to the local sandbox download directory (side effect).
            WHEN TO USE: user needs a file saved from a known http(s) URL.
            DO NOT USE: reading page text (use scrapeWebPage); searching (use searchWeb).
            For large/slow downloads prefer startDownloadResource + checkAsyncToolTask.
            Idempotent: repeating the same url+fileName within TTL returns the prior success without re-downloading.""")
    public String downloadResource(
            @ToolParam(description = "URL of the resource to download") String url,
            @ToolParam(description = "Target file name only (no directories / ..)") String fileName) {
        return doDownload(url, fileName);
    }

    @Tool(description = """
            Start an asynchronous download. Returns taskId immediately; poll with checkAsyncToolTask.
            WHEN TO USE: large files or prior downloadResource timed out.""")
    public String startDownloadResource(
            @ToolParam(description = "URL of the resource to download") String url,
            @ToolParam(description = "Target file name only (no directories / ..)") String fileName) {
        if (asyncToolTaskService == null) {
            return doDownload(url, fileName);
        }
        if (!UrlSafetyGuard.isSafeUrl(url)) {
            return UrlSafetyGuard.rejectMessage();
        }
        String taskId = asyncToolTaskService.submit(
                "downloadResource", "download " + fileName,
                () -> doDownload(url, fileName));
        return AsyncToolTaskService.submittedMessage(taskId, "downloadResource");
    }

    String doDownload(String url, String fileName) {
        if (!UrlSafetyGuard.isSafeUrl(url)) {
            return UrlSafetyGuard.rejectMessage();
        }
        String fingerprint = url + "::" + fileName;
        if (idempotencyStore != null) {
            String key = idempotencyStore.key("downloadResource", fingerprint);
            Optional<String> out = idempotencyStore.findOrRemember(key, () -> downloadOnce(url, fileName));
            return out.orElse("Error downloading resource: empty result");
        }
        return downloadOnce(url, fileName);
    }

    private String downloadOnce(String url, String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        try {
            Path basePath = Paths.get(fileDir).toAbsolutePath().normalize();
            Path resolved = basePath.resolve(fileName).normalize();
            if (!resolved.startsWith(basePath)) {
                return "拒绝下载：文件名包含非法路径，已被安全策略拦截。";
            }
            FileUtil.mkdir(fileDir);
            HttpUtil.downloadFile(url, new File(resolved.toString()));
            return "Resource downloaded successfully to: " + resolved;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
