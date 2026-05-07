package com.yupi.yuaiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.yupi.yuaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 资源下载工具（含路径穿越校验）
 */
public class ResourceDownloadTool {

    @Tool(description = "Download a resource from a given URL and save it to a local file")
    public String downloadResource(
            @ToolParam(description = "URL of the resource to download") String url,
            @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        try {
            // 路径穿越校验：防止 fileName 含 "../" 等跳出下载目录
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
