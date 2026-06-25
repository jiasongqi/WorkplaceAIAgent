package com.yupi.yuaiagent.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * 沙箱工厂 — 根据环境自动选择合适的沙箱实现。
 * <p>
 * 决策逻辑：
 * <ul>
 *     <li>Docker 可用 → {@link DockerSandbox}</li>
 *     <li>Docker 不可用 + 非生产环境 → {@link LocalProcessSandbox}（降级）</li>
 *     <li>Docker 不可用 + 生产环境 → 启动失败（{@link IllegalStateException}）</li>
 * </ul>
 *
 * @author jsq
 */
@Slf4j
@Component
public class SandboxFactory {

    @Value("${sandbox.require-docker:false}")
    private boolean requireDocker;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Resource
    private DockerSandbox dockerSandbox;

    @Resource
    private LocalProcessSandbox localProcessSandbox;

    private ToolSandbox selectedSandbox;
    private boolean dockerAvailable = false;

    @PostConstruct
    public void init() {
        dockerAvailable = checkDockerAvailable();

        if (dockerAvailable) {
            selectedSandbox = dockerSandbox;
            log.info("沙箱工厂: Docker 可用，使用 DockerSandbox");
        } else if (requireDocker || isProduction()) {
            throw new IllegalStateException(
                    "生产环境要求 Docker 可用，但检测到 Docker 未安装或未运行。" +
                    "请安装 Docker 或设置 sandbox.require-docker=false（仅限开发环境）。");
        } else {
            selectedSandbox = localProcessSandbox;
            log.warn("沙箱工厂: Docker 不可用，降级为 LocalProcessSandbox（仅限开发环境使用！）");
        }
    }

    /**
     * 获取当前选中的沙箱实现
     */
    public ToolSandbox getSandbox() {
        return selectedSandbox;
    }

    /**
     * Get sandbox by explicit policy. In production, requesting PROCESS_SANDBOX is forbidden.
     */
    public ToolSandbox getSandbox(SandboxPolicy policy) {
        return switch (policy) {
            case UNSANDBOXED -> null; // No sandbox needed
            case PROCESS_SANDBOX -> {
                if (isProduction()) {
                    throw new IllegalStateException(
                            "Production environment must not use LocalProcessSandbox. " +
                            "Use DockerSandbox instead.");
                }
                yield localProcessSandbox;
            }
            case DOCKER_SANDBOX -> {
                if (!dockerAvailable) {
                    throw new IllegalStateException("DockerSandbox requires Docker, but Docker is not available");
                }
                yield dockerSandbox;
            }
        };
    }

    /**
     * Docker 是否可用
     */
    public boolean isDockerAvailable() {
        return dockerAvailable;
    }

    /**
     * 当前选中的沙箱策略
     */
    public SandboxPolicy getActivePolicy() {
        return selectedSandbox != null ? selectedSandbox.getPolicy() : SandboxPolicy.UNSANDBOXED;
    }

    private boolean checkDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("Docker 检测失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean isProduction() {
        return "prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile);
    }
}
