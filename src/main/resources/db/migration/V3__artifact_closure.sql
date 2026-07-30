-- Reusable artifact publishing, recall and adoption ledger.
SET LOCAL lock_timeout = '5s';

ALTER TABLE t_artifact ADD COLUMN IF NOT EXISTS summary TEXT;
ALTER TABLE t_artifact ADD COLUMN IF NOT EXISTS reusable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE t_artifact ADD COLUMN IF NOT EXISTS target_agents TEXT;
ALTER TABLE t_artifact ADD COLUMN IF NOT EXISTS dedup_key VARCHAR(64);
ALTER TABLE t_artifact ADD COLUMN IF NOT EXISTS schema_version INTEGER;
ALTER TABLE t_artifact ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE t_artifact ADD COLUMN IF NOT EXISTS source_trace_id VARCHAR(64);

UPDATE t_artifact
SET status = 'PUBLISHED',
    reusable = TRUE,
    summary = COALESCE(summary, title),
    schema_version = COALESCE(schema_version, 1),
    target_agents = CASE type
        WHEN 'PROMOTION_PLAN' THEN 'GENERAL,NEGOTIATION'
        WHEN 'DATA_ANALYSIS_REPORT' THEN 'GENERAL,NEGOTIATION,RESUME'
        WHEN 'CAREER_COACH_ADVICE' THEN 'ESCAPE,GENERAL,NEGOTIATION,RESUME'
        WHEN 'USER_PROFILE_SUMMARY' THEN 'CONSULTATION,ESCAPE,GENERAL,NEGOTIATION,RESUME'
        WHEN 'LEARNING_RESOURCE_RECOMMENDATION' THEN 'GENERAL,RESUME'
        ELSE target_agents
    END
WHERE status = 'READY'
  AND type NOT IN ('MULTI_AGENT_DEBATE', 'AGENT_HANDOFF');

CREATE UNIQUE INDEX IF NOT EXISTS uk_artifact_dedup_key
    ON t_artifact(dedup_key)
    WHERE dedup_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_artifact_recall_task
    ON t_artifact(conversation_id, status, reusable, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_artifact_recall_profile
    ON t_artifact(user_id, status, reusable, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_artifact_expires_at
    ON t_artifact(expires_at)
    WHERE expires_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS t_artifact_adoption (
    id               BIGSERIAL PRIMARY KEY,
    artifact_id      VARCHAR(64) NOT NULL
                     REFERENCES t_artifact(artifact_id) ON DELETE CASCADE,
    consumer_agent   VARCHAR(64) NOT NULL,
    chat_id          VARCHAR(64),
    turn_id          VARCHAR(64) NOT NULL,
    stage            VARCHAR(16) NOT NULL,
    confidence       DOUBLE PRECISION,
    evidence         TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_artifact_adoption_stage UNIQUE (artifact_id, turn_id, stage)
);
CREATE INDEX IF NOT EXISTS idx_artifact_adoption_artifact
    ON t_artifact_adoption(artifact_id, stage);
CREATE INDEX IF NOT EXISTS idx_artifact_adoption_turn
    ON t_artifact_adoption(turn_id);
