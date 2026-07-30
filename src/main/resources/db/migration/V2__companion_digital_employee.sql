-- Personal companion + digital employee tables (Aily closed-loop parity)

CREATE TABLE IF NOT EXISTS t_user_companion (
    id               BIGSERIAL PRIMARY KEY,
    user_id          VARCHAR(64) NOT NULL UNIQUE,
    display_name     VARCHAR(128) NOT NULL DEFAULT '我的职场伙伴',
    persona_prompt   TEXT,
    style_prefs      JSONB DEFAULT '{}'::jsonb,
    enabled_skills   JSONB DEFAULT '[]'::jsonb,
    version          INTEGER NOT NULL DEFAULT 1,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_companion_user ON t_user_companion(user_id);

CREATE TABLE IF NOT EXISTS t_digital_employee (
    id               BIGSERIAL PRIMARY KEY,
    employee_id      VARCHAR(64) NOT NULL UNIQUE,
    owner_user_id    VARCHAR(64) NOT NULL,
    template_code    VARCHAR(128) NOT NULL,
    name             VARCHAR(128) NOT NULL,
    persona          TEXT,
    skill_bindings   JSONB DEFAULT '[]'::jsonb,
    status           VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    config_version   INTEGER NOT NULL DEFAULT 1,
    active           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_digital_employee_owner ON t_digital_employee(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_digital_employee_active ON t_digital_employee(owner_user_id, active);

CREATE TABLE IF NOT EXISTS t_digital_employee_version (
    id               BIGSERIAL PRIMARY KEY,
    employee_id      VARCHAR(64) NOT NULL,
    config_version   INTEGER NOT NULL,
    persona          TEXT,
    skill_bindings   JSONB DEFAULT '[]'::jsonb,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE (employee_id, config_version)
);
CREATE INDEX IF NOT EXISTS idx_de_version_employee ON t_digital_employee_version(employee_id);
