# Agent 体系 · 路由与子 Agent

> 核心路径：`src/main/java/com/yupi/yuaiagent/agent/`

---

## 主控 OrchestratorAgent

**文件：** `agent/OrchestratorAgent.java`  
**入口 API：** `GET /api/ai/orchestrator/chat` (SSE)  
**装配：** `config/AgentConfig.java`

### 路由流程

```
用户消息
  │
  ├─→ KeywordRouter           关键词快速路径（0 LLM）
  │     命中 → 直接路由到子 Agent
  │
  ├─→ SkillExecutor           YAML 技能匹配（skills/*.yaml）
  │     命中 → 技能直答
  │
  ├─→ NluPipeline             意图理解（1 次 LLM）
  │     ├─ 需澄清 → ClarificationHandler → SSE clarification 事件
  │     └─ 明确意图 → 路由
  │
  ├─→ ParadigmSelector        范式选择
  │     REACT → ToolCallAgent（默认）
  │     PLAN_AND_SOLVE → PlanAndSolveAgent
  │     REFLECTION → ReflectionAgent
  │
  └─→ 子 Agent 执行 → QualityGuard → TraceRecorder
```

### 意图 → 子 Agent 映射

| 意图 | 子 Agent | 文件 | 场景 |
|------|----------|------|------|
| `RESUME` | ResumeAgent | `agent/ResumeAgent.java` | 简历、面试、求职、offer |
| `NEGOTIATION` | NegotiationAgent | `agent/NegotiationAgent.java` | 薪资谈判、涨薪、绩效 |
| `ESCAPE` | EscapeAgent | `agent/EscapeAgent.java` | 离职、辞职、劳动纠纷 |
| `CONSULTATION` | ConsultationAgent | `agent/ConsultationAgent.java` | 预约咨询（状态机） |
| `GENERAL` | GeneralCareerAgent | `agent/GeneralCareerAgent.java` | 通用职场顾问 |

### 路由锁定

- ConsultationAgent 多轮信息收集期间锁定路由，完成/取消后解锁
- 跨 Agent 记忆：从 `PersistentMessageRepository` 取最近 10 条消息注入子 Agent

---

## 子 Agent 详情

### ResumeAgent — 求职顾问
- **RAG：** 检索求职篇文档
- **工具：** 可选联网搜索
- **Runner：** `agent/runner/ResumeAgentRunner.java`
- **权限：** `permissions/resume-agent.yaml`
- **描述符：** `agents/resume-agent.yaml`
- **评测：** `eval/resume-suite.yaml`

### NegotiationAgent — 薪资谈判
- **工具：** WebSearchTool（市场薪资数据）
- **Runner：** `agent/runner/NegotiationAgentRunner.java`
- **权限：** `permissions/negotiation-agent.yaml`

### EscapeAgent — 离职规划
- **工具：** PDFGenerationTool（离职手册）
- **Runner：** `agent/runner/EscapeAgentRunner.java`
- **权限：** `permissions/escape-agent.yaml`

### ConsultationAgent — 预约咨询
- **模式：** 状态机（收集信息 → 确认 → 创建日历事件）
- **日历：** `calendar/CalendarServiceFactory.java`（飞书/钉钉）
- **模板：** `templates/follow-up-templates.yml`
- **持久化：** `repository/AppointmentRepository.java`
- **校验：** `validation/InfoValidator.java`

### GeneralCareerAgent — 通用顾问
- **场景：** 人际关系、压力管理、职业规划
- **Runner：** `agent/runner/GeneralCareerAgentRunner.java`

---

## 超级智能体 YuManus

**文件：** `agent/YuManus.java`  
**入口：** `GET /api/ai/manus/chat` (SSE)  
**特点：** ReAct 自主规划 + 全工具循环，不经过 Orchestrator 路由

继承链：`BaseAgent → ReActAgent → ToolCallAgent → YuManus`

---

## 数据员工 Agent（黑板模式）

**基类：** `agent/data/DataEmployeeAgent.java`  
**产出：** 通过 `artifact/ArtifactShelf.java` 上架交付物

| Agent | 文件 | 产出类型 |
|-------|------|----------|
| DataAnalystAgent | `agent/data/DataAnalystAgent.java` | DATA_ANALYSIS_REPORT |
| CareerCoachAgent | `agent/data/CareerCoachAgent.java` | CAREER_COACH_ADVICE |
| ProfileCuratorAgent | `agent/data/ProfileCuratorAgent.java` | USER_PROFILE_SUMMARY |
| PromotionPlannerAgent | `agent/data/PromotionPlannerAgent.java` | PROMOTION_PLAN |
| LearningResourceRecommenderAgent | `agent/data/LearningResourceRecommenderAgent.java` | 学习资源推荐 |

---

## 范式 Agent

| 范式 | 类 | 适用场景 | 流程 |
|------|-----|----------|------|
| REACT | `ReActAgent` / `ToolCallAgent` | 交互式任务、工具调用 | Think → Act → Observe → Loop |
| PLAN_AND_SOLVE | `paradigm/PlanAndSolveAgent.java` | 复杂多步骤 | Plan → Execute → Verify |
| REFLECTION | `paradigm/ReflectionAgent.java` | 高质量输出 | Generate → Evaluate → Reflect → Revise |

**服务：** `paradigm/ParadigmService.java`  
**工厂：** `paradigm/ParadigmAgentFactory.java`

---

## 质量守护

**文件：** `quality/QualityGuardAgent.java`  
**触发：** Orchestrator 在子 Agent 输出后可选调用  
**维度：** 准确性、完整性、幻觉、安全性、格式  
**模式：** Review / RedTeam  
**SSE 事件：** `quality-review`, `quality-blocked`

---

## NLU 管道

**目录：** `nlu/`

| 类 | 职责 |
|----|------|
| `NluPipeline.java` | 单次 LLM 调用：意图 + 槽位 + 路由 |
| `ClarificationHandler.java` | 模糊意图追问 |
| `IntentAmbiguityDetector.java` | 意图歧义检测 |
| `RuleContextShiftDetector.java` | 上下文切换检测 |
| `ConversationState.java` | 对话状态管理 |

详细设计见 `docs/nlu-layer-design-v4.2.md`

---

## Agent 基类能力

**BaseAgent** (`agent/BaseAgent.java`)：
- 状态机 (`AgentState`)
- 步进循环 + SSE 流式输出
- Trace 集成
- Token 预算钩子

**相关接口/类：**
- `agent/AgentRunner.java` — Runner 接口
- `agent/AgentIntent.java` — 意图枚举
- `agent/DynamicPromptProvider.java` — 动态 Prompt
- `agent/DataQueryRouter.java` — 数据查询路由

---

## 修改 Agent 时的文件清单

| 步骤 | 文件 |
|------|------|
| 1. 创建 Agent 类 | `agent/XxxAgent.java` |
| 2. 注册 Bean | `config/AgentConfig.java` |
| 3. 添加路由 | `agent/OrchestratorAgent.java` |
| 4. NLU 意图 | `nlu/NluPipeline.java` + prompt |
| 5. Runner（可选） | `agent/runner/XxxAgentRunner.java` |
| 6. 权限画像 | `permissions/xxx-agent.yaml` |
| 7. 描述符（可选） | `agents/xxx-agent.yaml` |
| 8. 评测（可选） | `eval/xxx-suite.yaml` |
| 9. 测试 | `src/test/java/.../XxxAgentTest.java` |
