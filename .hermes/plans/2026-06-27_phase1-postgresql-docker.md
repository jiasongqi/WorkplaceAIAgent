# Phase 1 P0: PostgreSQL 持久化 + Docker 生产化

> **目标**：将文件存储迁移到 PostgreSQL，消除并发数据丢失风险，支持多实例部署。
> **工期**：2 个 Sprint（约 4 周）
> **创建**：2026-06-27

---

## 一、现状分析

### 1.1 当前存储模式

所有 Repository 使用同一模式：ConcurrentHashMap 内存缓存 + ReadWriteLock + JSON 文件持久化。

| 存储组件 | 文件位置 | 数据模型 | 启动加载 |
|---------|---------|---------|---------|
| ArtifactRepository | `./tmp/artifacts/artifacts.json` | `Map<String, Artifact>` | 全量加载到内存 |
| FeedbackRepository | `./tmp/feedback/feedback.json` | `List<Feedback>` | 全量加载到内存 |
| FactStoreLayer | `./tmp/memory/facts/{userId}.json` | `Map<userId, List<FactEntry>>` | 按文件加载 |
| ReflexionMemory | 内存 | `ConcurrentHashMap<userId, List<FailureMemory>>` | 无持久化 |
| TokenUsageTracker | 内存 | `ConcurrentHashMap<workflowId, TokenUsage>` | 无持久化 |
| ConsultationAgent 状态 | 内存 | 4 个 ConcurrentHashMap | 无持久化 |
| UserProfileService | 待确认（推测文件） | UserProfile 对象 | - |
| SessionStore | 待确认（推测文件） | 会话数据 | - |
| AppointmentRepository | 待确认（推测文件） | 预约数据 | - |
| TraceStore | 待确认（推测文件） | 执行轨迹 | - |

### 1.2 已有基础设施

- **Dockerfile** ✅ 已存在（maven:3.9-amazoncorretto-21，构建时打包）
- **PostgreSQL 驱动** ✅ 已在 pom.xml（PGVector 依赖）
- **application-prod.yml** ✅ 已配置 `spring.datasource` 和 PGVector
- **Actuator + Micrometer** ✅ 已配置
- **spring-boot-starter-jdbc** ✅ 已在 pom.xml

### 1.3 缺失项

- **spring-boot-starter-data-jpa** ❌ 未引入
- **Flyway** ❌ 无数据库迁移工具
- **docker-compose.yml** ❌ 不存在
- **Spring Data Repository 接口** ❌ 无，全部手写文件存储

---

## 二、数据库设计

### 2.1 核心表（13 张）

```sql
-- V1__init_schema.sql

-- 用户表
CREATE TABLE t_user (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL UNIQUE,
    nickname        VARCHAR(128),
    role            VARCHAR(32) DEFAULT 'USER',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 会话表
CREATE TABLE t_conversation (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    agent_type      VARCHAR(64),
    title           VARCHAR(256),
    status          VARCHAR(32) DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_conversation_user ON t_conversation(user_id);

-- 消息表（支撑 SSE 断线重连 + 部分回答保存）
CREATE TABLE t_message (
    id              BIGSERIAL PRIMARY KEY,
    message_id      VARCHAR(64) NOT NULL UNIQUE,
    conversation_id VARCHAR(64) NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         TEXT,
    partial_content TEXT,
    status          VARCHAR(16) DEFAULT 'COMPLETE',
    token_count     INTEGER,
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_message_conversation ON t_message(conversation_id);
CREATE INDEX idx_message_created ON t_message(created_at);

-- 交付物表
CREATE TABLE t_artifact (
    id              BIGSERIAL PRIMARY KEY,
    artifact_id     VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64),
    agent_type      VARCHAR(64),
    title           VARCHAR(256),
    type            VARCHAR(32),
    status          VARCHAR(32) DEFAULT 'PENDING',
    file_path       VARCHAR(512),
    file_size       BIGINT,
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_artifact_user ON t_artifact(user_id);

-- 用户事实表
CREATE TABLE t_user_fact (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    fact_key        VARCHAR(128) NOT NULL,
    fact_value      TEXT,
    category        VARCHAR(32),
    source          VARCHAR(64),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, fact_key)
);
CREATE INDEX idx_fact_user ON t_user_fact(user_id);

-- 用户画像表
CREATE TABLE t_user_profile (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL UNIQUE,
    profile_data    JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 反馈表
CREATE TABLE t_feedback (
    id              BIGSERIAL PRIMARY KEY,
    feedback_id     VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    agent_type      VARCHAR(64),
    rating          VARCHAR(8) NOT NULL,
    comment         TEXT,
    conversation_id VARCHAR(64),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_feedback_user ON t_feedback(user_id);
CREATE INDEX idx_feedback_agent ON t_feedback(agent_type);

-- 执行轨迹表
CREATE TABLE t_trace (
    id              BIGSERIAL PRIMARY KEY,
    trace_id        VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64),
    agent_type      VARCHAR(64),
    status          VARCHAR(16) DEFAULT 'RUNNING',
    spans           JSONB DEFAULT '[]',
    metadata        JSONB,
    started_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at    TIMESTAMP WITH TIME ZONE,
    total_ms        INTEGER
);
CREATE INDEX idx_trace_user ON t_trace(user_id);
CREATE INDEX idx_trace_conversation ON t_trace(conversation_id);

-- 反思记忆表
CREATE TABLE t_reflexion_memory (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64),
    failure_type    VARCHAR(64),
    context         TEXT,
    lesson          TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_reflexion_user ON t_reflexion_memory(user_id);

-- 预约表
CREATE TABLE t_appointment (
    id              BIGSERIAL PRIMARY KEY,
    appointment_id  VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    title           VARCHAR(256),
    scheduled_at    TIMESTAMP WITH TIME ZONE,
    status          VARCHAR(32) DEFAULT 'PENDING',
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_appointment_user ON t_appointment(user_id);

-- 会话存储表
CREATE TABLE t_chat_session (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64),
    state           JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_session_user ON t_chat_session(user_id);
CREATE INDEX idx_session_expires ON t_chat_session(expires_at);

-- Token 使用量表
CREATE TABLE t_token_usage (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64),
    model           VARCHAR(64),
    input_tokens    INTEGER DEFAULT 0,
    output_tokens   INTEGER DEFAULT 0,
    total_tokens    INTEGER DEFAULT 0,
    cost_usd        DECIMAL(10, 6) DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_token_usage_workflow ON t_token_usage(workflow_id);
CREATE INDEX idx_token_usage_user ON t_token_usage(user_id, created_at);

-- MCP 审计日志表
CREATE TABLE t_mcp_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    tool_name       VARCHAR(128) NOT NULL,
    server_id       VARCHAR(64),
    user_id         VARCHAR(64),
    status          VARCHAR(16),
    input_summary   TEXT,
    output_summary  TEXT,
    duration_ms     INTEGER,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_mcp_audit_tool ON t_mcp_audit_log(tool_name);
CREATE INDEX idx_mcp_audit_created ON t_mcp_audit_log(created_at);
```

### 2.2 设计要点

1. 所有时间字段用 `TIMESTAMP WITH TIME ZONE DEFAULT NOW()`（完整形式）
2. 业务 ID + 自增主键：`id` 自增用于内部关联，`xxx_id` UUID 用于 API 层
3. JSONB 扩展字段：metadata / profile_data / state 等半结构化数据
4. 无外键约束：应用层保证引用完整性，降低写入开销

---

## 三、代码改造方案

### 3.1 新增依赖（pom.xml）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

### 3.2 包结构

```
src/main/java/com/yupi/yuaiagent/
├── repository/
│   ├── entity/           # 13 个 JPA Entity
│   ├── jpa/              # 13 个 Spring Data JPA 接口
│   └── migration/        # FileToDbMigrator（JSON → DB 一次性迁移）
```

### 3.3 改造策略：接口兼容 + 内部替换

**核心原则**：不改 public API，只改内部实现。

```java
// Before: ConcurrentHashMap + JSON 文件
@Repository
public class ArtifactRepository {
    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
}

// After: JPA 实现（类名不变，调用方零改动）
@Repository
public class ArtifactRepository {
    private final ArtifactJpaRepository jpaRepo;

    public Artifact save(Artifact artifact) {
        return toDomain(jpaRepo.save(toEntity(artifact)));
    }
}
```

---

## 四、分批计划

### Sprint 1（第 1-2 周）：基础设施 + 核心表

| # | 任务 | 估时 | 依赖 |
|---|------|------|------|
| 1 | pom.xml 添加 JPA + Flyway | 0.5h | - |
| 2 | application.yml 配置 datasource + flyway | 1h | #1 |
| 3 | V1__init_schema.sql | 2h | - |
| 4 | Entity 类（13 个） | 4h | #3 |
| 5 | JPA Repository 接口（13 个） | 2h | #4 |
| 6 | ArtifactRepository 改造 + 测试 | 2h | #5 |
| 7 | FeedbackRepository 改造 + 测试 | 1h | #5 |
| 8 | FactStoreLayer 改造 + 测试 | 3h | #5 |
| 9 | ReflexionMemory 改造 + 测试 | 1h | #5 |
| 10 | docker-compose.yml | 2h | #2 |
| 11 | FileToDbMigrator | 3h | #6-9 |

### Sprint 2（第 3-4 周）：剩余组件 + SSE + 限流

| # | 任务 | 估时 | 依赖 |
|---|------|------|------|
| 12 | UserProfileService 改造 | 2h | Sprint 1 |
| 13 | SessionStore 改造 | 2h | Sprint 1 |
| 14 | AppointmentRepository 改造 | 1h | Sprint 1 |
| 15 | TraceStore 改造 | 2h | Sprint 1 |
| 16 | TokenUsageTracker 改造 | 1h | Sprint 1 |
| 17 | McpAuditLog 改造 | 1h | Sprint 1 |
| 18 | ConsultationAgent 状态持久化 | 2h | Sprint 1 |
| 19 | SSE 消息落库 | 4h | #13 |
| 20 | Resilience4j 限流 | 3h | - |
| 21 | 集成测试 | 4h | #12-20 |
| 22 | 生产配置优化 | 2h | #21 |

---

## 五、Docker 生产化

### 5.1 docker-compose.yml

```yaml
version: '3.8'

services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: workpilot-db
    environment:
      POSTGRES_DB: workpilot
      POSTGRES_USER: workpilot
      POSTGRES_PASSWORD: ${PG_PASSWORD:-workpilot123}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U workpilot"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: workpilot-redis
    ports:
      - "6379:6379"
    volumes:
      - redisdata:/data

  app:
    build: .
    container_name: workpilot-app
    ports:
      - "8123:8123"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      PG_DATASOURCE_URL: jdbc:postgresql://postgres:5432/workpilot
      PG_USERNAME: workpilot
      PG_PASSWORD: ${PG_PASSWORD:-workpilot123}
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY}
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - appdata:/data
    restart: unless-stopped

volumes:
  pgdata:
  redisdata:
  appdata:
```

### 5.2 Dockerfile 优化（多阶段构建）

```dockerfile
# Stage 1: Build
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM amazoncorretto:21-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /app/target/yu-ai-agent-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8123
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8123/api/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar", "--spring.profiles.active=prod"]
```

---

## 六、数据迁移

### 6.1 迁移策略

启动时检测 `./tmp/` 目录，如果 JSON 文件存在且 DB 对应表为空，执行一次性迁移。

### 6.2 回滚方案

迁移脚本不删除原 JSON 文件，只重命名 `.json.migrated`。如需回滚切换 profile 即可。

---

## 七、测试计划

- [ ] 每个 Repository 的 CRUD 操作
- [ ] 并发写入不丢数据（10 线程 × 100 条）
- [ ] JSON → DB 迁移正确性
- [ ] Flyway 幂等性
- [ ] Docker Compose 一键启动
- [ ] SSE 消息落库 + 断线恢复
- [ ] 所有现有集成测试通过

---

## 八、验收标准

- [ ] `docker-compose up` 一键启动 app + postgres + redis
- [ ] 所有 Repository 切换到 JPA，public API 不变
- [ ] JSON 文件数据可迁移到数据库
- [ ] 10 线程并发写入不丢数据
- [ ] 应用重启后数据完整恢复
- [ ] Flyway 迁移脚本可重复执行

---

## 九、待确认项

以下组件需要确认是否也使用文件存储：

1. UserProfileService — 是否有对应的 Repository？存储方式？
2. SessionStore — 会话状态存在哪里？
3. AppointmentRepository — 是否存在？存储方式？
4. TraceStore — 执行轨迹存在哪里？
5. McpAuditLog — 审计日志存在哪里？
6. ConsultationAgent 的 4 个 ConcurrentHashMap — 是否需要持久化？
