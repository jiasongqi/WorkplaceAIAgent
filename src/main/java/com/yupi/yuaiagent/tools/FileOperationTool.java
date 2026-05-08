package com.yupi.yuaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.yupi.yuaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件操作工具类（提供文件读写功能）
 * 安全加固：对 fileName 做路径穿越校验，防止 "../" 等跳出沙箱目录
 */
public class FileOperationTool {

    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    /**
     * 校验 fileName 解析后是否仍在 FILE_DIR 内，防止路径穿越攻击。
     * 返回规范化后的绝对路径；若校验失败则抛出 SecurityException。
     */
    private Path resolveAndValidate(String fileName) {
        Path basePath = Paths.get(FILE_DIR).toAbsolutePath().normalize();
        Path resolved = basePath.resolve(fileName).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("非法文件路径，已被安全策略拦截：" + fileName);
        }
        return resolved;
    }

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        try {
            Path filePath = resolveAndValidate(fileName);
            return FileUtil.readUtf8String(filePath.toFile());
        } catch (SecurityException e) {
            return "拒绝读取：" + e.getMessage();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content) {
        try {
            Path filePath = resolveAndValidate(fileName);
            // 创建目录
            FileUtil.mkdir(FILE_DIR);
            FileUtil.writeUtf8String(content, filePath.toFile());
            return "File written successfully to: " + filePath;
        } catch (SecurityException e) {
            return "拒绝写入：" + e.getMessage();
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}
