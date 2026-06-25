package com.yupi.yuaiagent.sandbox;

/**
 * 沙箱执行策略 — 描述 Tool 使用的隔离方式，而非限制程度。
 * <p>
 * 设计原则：描述的是执行方式（how），而不是限制程度（what）。
 *
 * @author jsq
 */
public enum SandboxPolicy {

    /**
     * 不使用沙箱 — 适用于纯计算型安全 Tool（如 PDF 生成、字符串处理）
     */
    UNSANDBOXED,

    /**
     * 本地进程沙箱 — 基于 ProcessBuilder + 5 层防护
     * （命令白名单、工作目录隔离、超时、输出限制、环境变量隔离）
     * <p>
     * 仅在 Docker 不可用时作为降级方案
     */
    PROCESS_SANDBOX,

    /**
     * Docker 沙箱 — 基于 Docker 容器的完全隔离
     * （CPU、Memory、Network、Filesystem 全隔离）
     * <p>
     * 生产环境强制要求此模式
     */
    DOCKER_SANDBOX
}
