-- S5a ExpertPack preference persistence (JDBC mode)
CREATE TABLE IF NOT EXISTS t_expert_pack_preference (
    user_id     VARCHAR(64)  NOT NULL PRIMARY KEY,
    mode        VARCHAR(64)  NOT NULL,
    packs_json  TEXT         NOT NULL,
    version     BIGINT       NOT NULL
);
