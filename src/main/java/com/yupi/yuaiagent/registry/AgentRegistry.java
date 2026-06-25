package com.yupi.yuaiagent.registry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Agent 注册中心接口 — Agent Marketplace 的核心发现机制。
 *
 * @author jsq
 */
public interface AgentRegistry {

    /**
     * 注册 Agent 描述符
     */
    void register(AgentDescriptor descriptor);

    /**
     * 按 agentCode 获取
     */
    Optional<AgentDescriptor> get(String agentCode);

    /**
     * 列出所有已注册 Agent
     */
    Collection<AgentDescriptor> list();

    /**
     * 按能力标签查找
     */
    List<AgentDescriptor> findByCapability(String capability);

    /**
     * 按意图关键词匹配
     */
    List<AgentDescriptor> findByIntentKeyword(String keyword);

    /**
     * 获取已注册数量
     */
    int size();
}
