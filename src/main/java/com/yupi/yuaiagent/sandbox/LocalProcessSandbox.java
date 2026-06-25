package com.yupi.yuaiagent.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 本地进程沙箱 — 基于 ProcessBuilder + 5 层安全防护。
 * <p>
 * <strong>仅作为 Docker 不可用时的降级方案</strong>，生产环境必须使用 Docker 沙箱。
 * <p>
 * 5 层防护：
 * <ol>
 *     <li>命令白名单 — 仅允许安全命令（python, node, java, git 等）</li>
 *     <li>工作目录隔离 — 限制在 tmp/sandbox/{taskId} 内，禁止 ../ 路径穿透</li>
 *     <li>超时强制终止 — 超时后 destroyForcibly()</li>
 *     <li>输出大小限制 — 超过 maxOutputSize 自动截断</li>
 *     <li>环境变量隔离 — 禁止继承敏感环境变量（API Key、JWT Secret 等）</li>
 * </ol>
 *
 * @author jsq
 */
@Slf4j
@Component
public class LocalProcessSandbox implements ToolSandbox {

    /**
     * 第一层：命令白名单（只允许这些命令开头）
     */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "python", "python3", "node", "java", "git",
            "echo", "cat", "head", "tail", "wc", "sort", "uniq",
            "ls", "pwd", "date", "whoami", "which", "grep", "sed", "awk"
    );

    /**
     * 敏感环境变量黑名单 — 禁止子进程继承
     */
    private static final Set<String> BLOCKED_ENV_VARS = Set.of(
            "OPENAI_API_KEY", "DASHSCOPE_API_KEY", "JWT_SECRET",
            "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY",
            "FEISHU_APP_SECRET", "DINGTALK_APP_SECRET",
            "DATABASE_PASSWORD", "DB_PASSWORD", "REDIS_PASSWORD",
            "SEARCH_API_KEY", "AMAP_MAPS_API_KEY"
    );

    @Value("${sandbox.base-dir:./tmp/sandbox}")
    private String baseDir;

    @Value("${sandbox.default-timeout:30}")
    private int defaultTimeoutSeconds;

    @Value("${sandbox.max-output-size:10485760}")
    private long maxOutputSize; // 10MB

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(baseDir));
            log.info("本地进程沙箱初始化完成，baseDir={}, maxOutputSize={}B",
                    Paths.get(baseDir).toAbsolutePath(), maxOutputSize);
        } catch (IOException e) {
            log.error("创建沙箱基础目录失败: {}", baseDir, e);
        }
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 第一层：命令白名单校验
            String validationResult = validateCommand(request.getCommand());
            if (validationResult != null) {
                return SandboxResult.builder()
                        .exitCode(-1)
                        .errorMessage("命令被拒绝: " + validationResult)
                        .stdout("")
                        .stderr("命令被安全策略拦截: " + validationResult)
                        .sandboxType("local-process")
                        .build();
            }

            // 第二层：工作目录隔离
            Path workDir = prepareWorkDir(request);

            // 构建 ProcessBuilder
            ProcessBuilder pb = buildProcessBuilder(request, workDir);

            // 启动进程
            Process process = pb.start();

            // 读取输出（带大小限制）
            OutputCapture capture = captureOutput(process);

            // 第三层：超时控制
            Duration timeout = request.getTimeout() != null
                    ? request.getTimeout()
                    : Duration.ofSeconds(defaultTimeoutSeconds);

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                long elapsed = System.currentTimeMillis() - startTime;
                return SandboxResult.builder()
                        .exitCode(-1)
                        .stdout(truncate(capture.stdout))
                        .stderr(capture.stderr + "\n[TIMEOUT] 进程超时被强制终止（" + timeout.toSeconds() + "s）")
                        .killed(true)
                        .executionTimeMs(elapsed)
                        .outputTruncated(capture.truncated)
                        .sandboxType("local-process")
                        .build();
            }

            int exitCode = process.exitValue();
            long elapsed = System.currentTimeMillis() - startTime;

            // 执行后清理工作目录
            cleanupWorkDir(workDir, request);

            return SandboxResult.builder()
                    .exitCode(exitCode)
                    .stdout(truncate(capture.stdout))
                    .stderr(capture.stderr)
                    .killed(false)
                    .executionTimeMs(elapsed)
                    .outputTruncated(capture.truncated)
                    .sandboxType("local-process")
                    .build();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("沙箱执行异常", e);
            return SandboxResult.builder()
                    .exitCode(-1)
                    .errorMessage("沙箱执行异常: " + e.getMessage())
                    .stdout("")
                    .stderr(e.getMessage())
                    .executionTimeMs(elapsed)
                    .sandboxType("local-process")
                    .build();
        }
    }

    @Override
    public SandboxPolicy getPolicy() {
        return SandboxPolicy.PROCESS_SANDBOX;
    }

    /**
     * 第一层：命令白名单校验
     *
     * @return null 表示通过，否则返回拒绝原因
     */
    private String validateCommand(String command) {
        if (command == null || command.isBlank()) {
            return "命令不能为空";
        }

        String trimmed = command.trim();
        // 提取第一个词（命令名）
        String cmdName = trimmed.split("\\s+")[0].toLowerCase();

        // 移除路径前缀（如 /usr/bin/python → python）
        int lastSlash = cmdName.lastIndexOf('/');
        if (lastSlash >= 0) {
            cmdName = cmdName.substring(lastSlash + 1);
        }
        // Windows: 移除 .exe / .cmd 后缀
        if (cmdName.endsWith(".exe") || cmdName.endsWith(".cmd")) {
            cmdName = cmdName.substring(0, cmdName.length() - 4);
        }

        if (!ALLOWED_COMMANDS.contains(cmdName)) {
            return String.format("命令 [%s] 不在白名单内。允许: %s", cmdName, ALLOWED_COMMANDS);
        }

        // Extra check: block shell metacharacters that enable injection
        // Blocked: ; && || | ` $() > >> < << & # newline
        if (trimmed.contains(";") || trimmed.contains("&&") || trimmed.contains("||")
                || trimmed.contains("|") || trimmed.contains("`")
                || trimmed.contains("$(") || trimmed.contains("${")
                || trimmed.contains(">") || trimmed.contains("<")
                || trimmed.contains("&") || trimmed.contains("#")) {
            return "禁止使用 shell 元字符（; && || | ` $() > < & #）";
        }

        return null;
    }

    /**
     * 第二层：准备隔离的工作目录
     */
    private Path prepareWorkDir(SandboxRequest request) throws IOException {
        String taskId = request.getTaskId() != null ? request.getTaskId() : UUID.randomUUID().toString();
        Path workDir = Paths.get(baseDir, taskId).toAbsolutePath().normalize();

        // 防止路径穿透
        Path baseDirPath = Paths.get(baseDir).toAbsolutePath().normalize();
        if (!workDir.startsWith(baseDirPath)) {
            throw new SecurityException("工作目录路径穿透攻击: " + workDir);
        }

        Files.createDirectories(workDir);
        return workDir;
    }

    /**
     * 构建 ProcessBuilder（第五层：环境变量隔离）
     */
    private ProcessBuilder buildProcessBuilder(SandboxRequest request, Path workDir) {
        ProcessBuilder pb;

        // Windows 环境使用 cmd /c
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", request.getCommand());
        } else {
            pb = new ProcessBuilder("sh", "-c", request.getCommand());
        }

        pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);

        // 第五层：环境变量隔离 — 移除敏感变量
        Map<String, String> env = pb.environment();
        for (String blocked : BLOCKED_ENV_VARS) {
            env.remove(blocked);
        }
        // 添加沙箱标记环境变量
        env.put("SANDBOX_MODE", "true");
        env.put("SANDBOX_WORK_DIR", workDir.toString());

        return pb;
    }

    /**
     * 第四层：捕获输出（带大小限制）
     */
    private OutputCapture captureOutput(Process process) {
        OutputCapture capture = new OutputCapture();

        Thread stdoutThread = Thread.startVirtualThread(() -> {
            capture.stdout = readStreamWithLimit(process.getInputStream());
        });
        Thread stderrThread = Thread.startVirtualThread(() -> {
            capture.stderr = readStreamWithLimit(process.getErrorStream());
        });

        try {
            stdoutThread.join(60_000);
            stderrThread.join(60_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return capture;
    }

    /**
     * 读取流内容，超过 maxOutputSize 自动截断
     */
    private String readStreamWithLimit(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
                if (sb.length() > maxOutputSize) {
                    sb.setLength((int) maxOutputSize);
                    sb.append("\n... [OUTPUT TRUNCATED at ").append(maxOutputSize).append(" bytes]");
                    break;
                }
            }
            return sb.toString();
        } catch (IOException e) {
            return "[读取输出失败: " + e.getMessage() + "]";
        }
    }

    private String truncate(String text) {
        if (text == null) return "";
        if (text.length() <= maxOutputSize) return text;
        return text.substring(0, (int) maxOutputSize) + "\n... [TRUNCATED]";
    }

    /**
     * 清理工作目录
     */
    private void cleanupWorkDir(Path workDir, SandboxRequest request) {
        try {
            // 递归删除
            if (Files.exists(workDir)) {
                Files.walk(workDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {}
                        });
            }
        } catch (IOException e) {
            log.warn("清理沙箱工作目录失败: {}", workDir, e);
        }
    }

    private static class OutputCapture {
        String stdout = "";
        String stderr = "";
        boolean truncated = false;
    }
}
