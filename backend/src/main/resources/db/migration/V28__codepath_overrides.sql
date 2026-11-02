CREATE TABLE codepath_overrides (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    path_id    BIGINT       NOT NULL REFERENCES codepaths (id) ON DELETE CASCADE,
    node_id    VARCHAR(80)  NOT NULL,
    kind       VARCHAR(30)  NOT NULL,
    target     VARCHAR(80)  NOT NULL DEFAULT '',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    UNIQUE (user_id, path_id, node_id, kind, target)
);

CREATE INDEX idx_codepath_overrides_user_path ON codepath_overrides (user_id, path_id);
