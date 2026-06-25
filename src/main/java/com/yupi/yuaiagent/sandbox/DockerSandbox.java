package com.yupi.yuaiagent.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Docker 沙箱 — 基于 Docker 容器的完全隔离执行环境。
 * <p>
 * 隔离维度：
 * <ul>
 *     <li>CPU — 通过 {@code --cpus} 限制</li>
 *     <li>Memory — 通过 {@code --memory} 限制</li>
 *     <li>Network — 通过 {@code --network none} 禁用（默认）</li>
 *     <li>Filesystem — 仅挂载临时工作目录，容器内无法访问宿主机其他文件</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Component
public class DockerSandbox implements ToolSandbox {

    @Value("${sandbox.docker.image:python:3.12-slim}")
    private String dockerImage;

    @Value("${sandbox.docker.default-timeout:30}")
    private int defaultTimeoutSeconds;

    @Value("${sandbox.base-dir:./tmp/sandbox}")
    private String baseDir;

    @Value("${sandbox.max-output-size:10485760}")
    private long maxOutputSize;

    @PostConstruct
    public void init() {
        log.info("Docker 沙箱初始化完成，默认镜像: {}, 超时: {}s", dockerImage, defaultTimeoutSeconds);
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        long startTime = System.currentTimeMillis();
        String containerName = "sandbox-" + UUID.randomUUID().toString().substring(0, 8);
        ResourceLimits limits = request.getResourceLimits() != null
                ? request.getResourceLimits()
                : ResourceLimits.defaults();

        try {
            // 准备宿主工作目录
            String taskId = request.getTaskId() != null ? request.getTaskId() : UUID.randomUUID().toString();
            File hostWorkDir = new File(baseDir, taskId);
            hostWorkDir.mkdirs();

            // 构建 docker run 命令
            List<String> dockerCmd = buildDockerCommand(containerName, request, limits, hostWorkDir);

            log.debug("Docker 命令: {}", String.join(" ", dockerCmd));

            ProcessBuilder pb = new ProcessBuilder(dockerCmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Close stdin
            process.getOutputStream().close();

            // Capture stdout and stderr in parallel (avoid deadlock when one buffer fills)
            OutputCapture capture = captureOutputParallel(process);

            // Timeout control
            Duration timeout = request.getTimeout() != null
                    ? request.getTimeout()
                    : Duration.ofSeconds(defaultTimeoutSeconds);

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                // Timeout: force stop container
                forceStopContainer(containerName);
                process.destroyForcibly();
                long elapsed = System.currentTimeMillis() - startTime;

                return SandboxResult.builder()
                        .exitCode(-1)
                        .stdout(truncate(capture.stdout))
                        .stderr(capture.stderr + "\n[TIMEOUT] 容器超时被强制终止（" + timeout.toSeconds() + "s）")
                        .killed(true)
                        .executionTimeMs(elapsed)
                        .sandboxType("docker")
                        .build();
            }

            int exitCode = process.exitValue();
            long elapsed = System.currentTimeMillis() - startTime;

            // Cleanup container
            removeContainer(containerName);
            // Cleanup host work directory
            cleanupDir(hostWorkDir);

            return SandboxResult.builder()
                    .exitCode(exitCode)
                    .stdout(truncate(capture.stdout))
                    .stderr(capture.stderr)
                    .killed(false)
                    .executionTimeMs(elapsed)
                    .sandboxType("docker")
                    .build();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Docker 沙箱执行异常", e);
            forceStopContainer(containerName);
            return SandboxResult.builder()
                    .exitCode(-1)
                    .errorMessage("Docker 沙箱执行异常: " + e.getMessage())
                    .stdout("")
                    .stderr(e.getMessage())
                    .executionTimeMs(elapsed)
                    .sandboxType("docker")
                    .build();
        }
    }

    @Override
    public SandboxPolicy getPolicy() {
        return SandboxPolicy.DOCKER_SANDBOX;
    }

    /**
     * 构建 docker run 命令
     */
    private List<String> buildDockerCommand(String containerName, SandboxRequest request,
                                             ResourceLimits limits, File hostWorkDir) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(containerName);

        // CPU 限制
        cmd.add("--cpus");
        cmd.add(String.valueOf(limits.getMaxCpus()));

        // 内存限制
        cmd.add("--memory");
        cmd.add(limits.getMaxMemoryMb() + "m");

        // 网络隔离
        if (limits.isNetworkDisabled()) {
            cmd.add("--network");
            cmd.add("none");
        }

        // 安全加固：只读根文件系统（工作目录除外）
        cmd.add("--read-only");

        // 挂载工作目录
        cmd.add("-v");
        cmd.add(hostWorkDir.getAbsolutePath() + ":/workspace:rw");
        cmd.add("--tmpfs");
        cmd.add("/tmp:rw,noexec,nosuid,size=64m");

        // 工作目录
        cmd.add("-w");
        cmd.add("/workspace");

        // 环境变量隔离（不传递宿主环境变量）
        cmd.add("-e");
        cmd.add("SANDBOX_MODE=true");

        // 使用指定镜像
        cmd.add(dockerImage);

        // 执行命令
        cmd.add("sh");
        cmd.add("-c");
        cmd.add(request.getCommand());

        return cmd;
    }

    /**
     * 强制停止容器
     */
    private void forceStopContainer(String containerName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "kill", containerName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("停止容器 {} 失败: {}", containerName, e.getMessage());
        }
    }

    /**
     * 移除容器
     */
    private void removeContainer(String containerName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "rm", "-f", containerName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }

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
     * Capture stdout and stderr in parallel using virtual threads.
     * Sequential reading can deadlock when one pipe buffer fills while the other is being read.
     */
    private OutputCapture captureOutputParallel(Process process) {
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

    private static class OutputCapture {
        String stdout = "";
        String stderr = "";
    }

    private void cleanupDir(File dir) {
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            dir.delete();
        }
    }
}
