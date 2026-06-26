# Hello-Agents 课程总结 & agent_product 差距分析

> 来源: https://hello-agents.datawhale.cc/#/
> 对标项目: agent_product (WorkPilot - AI 职业教练)

---

## 课程大纲总览

| 部分 | 章节 | 关键内容 | 状态 |
|------|------|----------|------|
| **第一部分: 基础** | Ch1 初识智能体 | 定义、类型、范式、应用 | ✅ |
| | Ch2 智能体发展史 | 符号主义 → LLM Agent 演进 | ✅ |
| | Ch3 大语言模型基础 | Transformer、提示、主流LLM、局限 | ✅ |
| **第二部分: 构建** | Ch4 经典范式构建 | ReAct、Plan-and-Solve、Reflection | ✅ |
| | Ch5 低代码平台 | Coze、Dify、n8n | ✅ |
| | Ch6 框架开发实践 | AutoGen、AgentScope、LangGraph | ✅ |
| | Ch7 构建你的Agent框架 | 从0构建 HelloAgents 框架 | ✅ |
| **第三部分: 高级** | Ch8 记忆与检索 | Memory System + RAG | ✅ |
| | Ch9 上下文工程 | ContextBuilder、GSSC流水线 | ✅ |
| | Ch10 通信协议 | MCP、A2A、ANP | ✅ |
| | Ch11 Agentic-RL | SFT → GRPO 训练LLM | ✅ |
| | Ch12 性能评估 | BFCL、GAIA、LLM Judge | ✅ |
| **第四部分: 实战** | Ch13 智能旅行助手 | 综合项目 | ✅ |
| | Ch14 深度研究智能体 | 自动化研究 | ✅ |
| | Ch15 赛博小镇 | 多Agent仿真 | ✅ |
| **第五部分: 毕业** | Ch16 毕业设计 | 面试 & 项目 | ✅ |

---

## 各章核心知识点详解

### Ch4: 智能体经典范式构建 (46分钟)

**ReAct (Reasoning + Acting)**
- Thought → Action → Observation 循环
- 边想边做，动态调整策略
- 工具定义: name/description/parameters/run()
- 关键: 解析LLM输出中的 [TOOL_CALL] 标记

**Plan-and-Solve**
- 先规划完整行动计划，再逐步执行
- 规划阶段: 任务分解为有序步骤
- 执行器: 按计划调用工具，管理状态
- 适合: 步骤明确、可预测的任务

**Reflection**
- 自我批判 + 修正机制
- 记忆模块: 存储历史反思
- 成本收益: 额外LLM调用 vs 质量提升
- 适合: 需要高质量输出的场景

---

### Ch7: 构建你的Agent框架

**HelloAgents 框架架构:**
```
HelloAgents/
├── core/           # 核心抽象
│   ├── Agent       # 基类
│   ├── Tool        # 工具接口
│   └── LLM         # LLM封装
├── agents/         # 具体范式实现
│   ├── SimpleAgent
│   ├── ReActAgent
│   ├── PlanSolveAgent
│   └── ReflectionAgent
├── tools/          # 内置工具
│   ├── CalculatorTool
│   ├── SearchTool
│   └── FileTool
└── utils/          # 工具函数
```

**核心设计模式:**
- Agent 基类: name, system_prompt, llm, tools
- Tool 接口: name, description, parameters_schema, run()
- LLM 封装: 统一 API 调用，支持多 provider

---

### Ch8: 记忆与检索 (50分钟)

**记忆系统 (Memory System)**
- 4种记忆类型:
  1. 感觉记忆 (Sensory) - 极短，0.5-3秒
  2. 短期记忆 (Short-term) - 15-30秒，7±2项
  3. 工作记忆 (Working) - 当前任务上下文
  4. 长期记忆 (Long-term) - 持久化存储
- MemoryTool: add/search/delete/list
- MemoryManager: 自动摘要、重要性评分、过期清理

**RAG 系统 (Retrieval-Augmented Generation)**
- 基础流程: 文档加载 → 分块 → 嵌入 → 索引 → 检索 → 生成
- 高级策略:
  - 混合检索 (向量 + 关键词)
  - 重排序 (Reranking)
  - 查询改写 (Query Rewriting)
  - 多跳检索 (Multi-hop)

---

### Ch9: 上下文工程 (54分钟)

**核心概念:**
- Prompt Engineering → Context Engineering 演进
- 上下文 = 系统指令 + 工具 + MCP + 外部数据 + 消息历史

**ContextBuilder 组件:**
- GSSC 流水线: Gather → Select → Summarize → Compose
- 动态上下文组装
- 令牌预算管理

**配套工具:**
- NoteTool: 结构化笔记 (CRUD)
- TerminalTool: 文件系统访问 + 安全沙箱

**长程任务管理:**
- 任务状态持久化
- 断点续做
- 进度追踪

---

### Ch10: 智能体通信协议 (48分钟)

**三种协议对比:**

| 协议 | 定位 | 适用场景 |
|------|------|----------|
| **MCP** | Agent ↔ Tool | 标准化工具集成 |
| **A2A** | Agent ↔ Agent | 点对点协作 |
| **ANP** | Agent ↔ Network | 大规模Agent网络 |

**MCP 协议:**
- 传输方式: stdio, HTTP/SSE
- 工具发现: 动态注册
- 社区生态: MCP Hub

**A2A 协议:**
- Agent Card: 能力声明
- Task: 任务协作
- Message: 消息传递

**ANP 协议:**
- 服务发现
- 路由与负载均衡
- 分布式Agent网络

---

### Ch12: 智能体性能评估 (58分钟)

**三大评估维度:**

1. **BFCL (工具调用能力)**
   - 函数签名正确性
   - 参数提取准确率
   - 多工具协调能力

2. **GAIA (通用AI助手)**
   - 多步骤推理
   - 工具组合使用
   - 真实世界问题

3. **数据生成质量**
   - LLM Judge: 模型打分
   - Win Rate: 对比评估
   - 人工验证: 界面化审核

**评估报告:**
- 综合得分
- 分维度分析
- 改进建议

---

## agent_product 差距分析

### ✅ 已实现 (可跳过)

| 能力 | agent_product 状态 | 说明 |
|------|-------------------|------|
| ReAct 范式 | ✅ ReActAgent.java | 完整实现 |
| 工具系统 | ✅ ToolCallAgent + MCP Gateway | Spring AI ToolCallback |
| 记忆系统 | ✅ MemoryCoordinator + 多层架构 | SummaryLayer + ExperienceStoreLayer + ProceduralMemory |
| 上下文压缩 | ✅ FactPreservingCompressor | 事实保留压缩 |
| RAG 检索 | ✅ RagTool + HyDERetriever | PGVector + HyDE + 多查询 |
| MCP 协议 | ✅ spring-ai-starter-mcp-client | 已集成 |
| 超时控制 | ✅ Tool Timeout 30s | Sprint-1 完成 |
| 质量守护 | ✅ QualityGuardAgent | 分级守护 |
| 安全防护 | ✅ PromptInjectionDetector + EmbeddingLoopDetector | 注入检测 + 死循环检测 |
| NLU | ✅ NluPipeline + KeywordRouter + DynamicPromptProvider | V4 方案 |
| 工作流 | ✅ WorkflowRuntime | 运行时引擎 |
| 反馈系统 | ✅ FeedbackController + FeedbackRepository | 用户反馈闭环 |
| 效率追踪 | ✅ AgentEfficiencyTracker | 使用统计 |
| PDF 生成 | ✅ iTextPDF | 简历等文档生成 |

### 🔴 待实现 (优先级排序)

| # | 能力 | 来源章节 | 优先级 | ROI 评估 | 工作量 |
|---|------|----------|--------|----------|--------|
| 1 | **Actuator 指标暴露** | Ch12 评估 | P0 | 高 - 监控基础 | 0.5天 |
| 2 | **压测基准 (Benchmark)** | Ch12 | P0 | 高 - 证明稳定性 | 1天 |
| 3 | **Reflection 自我修正** | Ch4 | P1 | 中 - 提升输出质量 | 2天 |
| 4 | **A2A 协议支持** | Ch10 | P2 | 中 - 多Agent协作 | 3天 |
| 5 | **ContextBuilder 优化** | Ch9 | P2 | 中 - 精细化上下文 | 2天 |
| 6 | **BFCL/GAIA 评估** | Ch12 | P2 | 中 - 标准化评估 | 2天 |
| 7 | **Agentic-RL 训练** | Ch11 | P3 | 低 - 需要GPU资源 | 5天+ |

### 📋 建议执行顺序

```
Sprint-2 (当前 - 监控 & 稳定性):
├── [P0] Actuator 指标暴露
│   ├── 添加 spring-boot-starter-actuator 依赖
│   ├── 配置 /actuator/metrics, /actuator/health
│   ├── 自定义 Agent 指标 (tool_call_count, llm_latency, error_rate)
│   └── Grafana/Prometheus 集成
└── [P0] 压测基准
    ├── JMeter/Gatling 压测脚本
    ├── 并发场景: 10/50/100 用户
    ├── 关键指标: P50/P95/P99 延迟, 吞吐量, 错误率
    └── 压测报告 + 瓶颈分析

Sprint-3 (下一轮 - 质量提升):
├── [P1] Reflection 自我修正机制
│   ├── 输出质量评估 (QualityGuard 已有基础)
│   ├── 自动修正循环 (最多 N 轮)
│   └── 成本控制 (Token 预算)
├── [P2] A2A 协议基础支持
│   ├── Agent Card 能力声明
│   └── Task 协作消息格式
└── [P2] ContextBuilder 优化
    ├── GSSC 流水线 (Gather → Select → Summarize → Compose)
    └── 令牌预算管理

Sprint-4 (后续 - 评估体系):
├── [P2] BFCL 工具调用评估
│   ├── 函数签名正确性测试
│   └── 参数提取准确率
└── [P3] 其他高级特性
```

---

## 参考资源

- 课程仓库: https://github.com/datawhalechina/hello-agents
- 在线阅读: https://hello-agents.datawhale.cc/
- HelloAgents 框架: hello-agents PyPI 包
