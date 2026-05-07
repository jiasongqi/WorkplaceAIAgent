package com.yupi.yuaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 终端操作工具（含命令安全校验）
 */
public class TerminalOperationTool {

    // 危险命令黑名单（正则匹配命令开头）
    private static final Set<Pattern> BLOCKED_PATTERNS = Set.of(
            Pattern.compile("^(rm|del|erase)\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(rmdir|rd)\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^format\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^shutdown\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^reboot\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^mkfs\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^dd\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^:(\\s*)\\{.*\\|.*&.*\\}.*", Pattern.CASE_INSENSITIVE), // fork bomb
            Pattern.compile(".*>\\s*/dev/.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(net\\s+user|net\\s+localgroup)\\b.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^reg\\s+(delete|add)\\b.*", Pattern.CASE_INSENSITIVE)
    );

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute in the terminal") String command) {
        // 安全校验
        String trimmed = command.trim();
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(trimmed).matches()) {
                return "拒绝执行：命令 [" + trimmed + "] 包含危险操作，已被安全策略拦截。";
            }
        }

        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }
        } catch (IOException | InterruptedException e) {
            output.append("Error executing command: ").append(e.getMessage());
        }
        return output.toString();
    }
}
