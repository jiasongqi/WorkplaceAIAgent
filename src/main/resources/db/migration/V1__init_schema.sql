-- V1__init_schema.sql
-- WorkPilot database schema - Phase 1 P0 migration

-- User table
CREATE TABLE t_wp_user (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL UNIQUE,
    nickname        VARCHAR(128),
    role            VARCHAR(32) DEFAULT 'USER',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Conversation table
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

-- Message table (supports SSE reconnection + partial answer save)
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

-- Artifact table
CREATE TABLE t_artifact (
    id              BIGSERIAL PRIMARY KEY,
    artifact_id     VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64),
    agent_type      VARCHAR(64),
    title           VARCHAR(256),
    type            VARCHAR(32),
    status          VARCHAR(32) DEFAULT 'PENDING',
    scope           VARCHAR(32),
    content         TEXT,
    file_path       VARCHAR(512),
    file_size       BIGINT,
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_artifact_user ON t_artifact(user_id);

-- User fact table
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

-- User profile table
CREATE TABLE t_wp_user_profile (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL UNIQUE,
    profile_data    JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Feedback table
CREATE TABLE t_feedback (
    id              BIGSERIAL PRIMARY KEY,
    feedback_id     VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    chat_id         VARCHAR(64),
    message_id      VARCHAR(64),
    agent_type      VARCHAR(64),
    rating          VARCHAR(8) NOT NULL,
    comment         TEXT,
    intent          VARCHAR(64),
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_feedback_user ON t_feedback(user_id);
CREATE INDEX idx_feedback_agent ON t_feedback(agent_type);

-- Trace table
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

-- Reflexion memory table
CREATE TABLE t_reflexion_memory (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(64),
    failure_type    VARCHAR(64),
    error           TEXT,
    resolution      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_reflexion_user ON t_reflexion_memory(user_id);

-- Appointment table
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

-- Chat session table
CREATE TABLE t_chat_session (
    id              BIGSERIAL PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL UNIQUE,
    user_id         VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64),
    title           VARCHAR(256),
    status          VARCHAR(32) DEFAULT 'ACTIVE',
    state           JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_active_at  TIMESTAMP WITH TIME ZONE,
    archived_at     TIMESTAMP WITH TIME ZONE,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_session_user ON t_chat_session(user_id);
CREATE INDEX idx_session_expires ON t_chat_session(expires_at);

-- Token usage table
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

-- MCP audit log table
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
