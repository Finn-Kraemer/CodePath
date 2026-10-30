CREATE TABLE learning_path_branch_results (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    path_id BIGINT NOT NULL REFERENCES learning_paths(id) ON DELETE CASCADE,
    edge_key VARCHAR(120) NOT NULL,
    matched BOOLEAN NOT NULL,
    rule VARCHAR(40),
    detail VARCHAR(255),
    decided_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_learning_path_branch_results UNIQUE (user_id, path_id, edge_key)
);

CREATE INDEX idx_lp_branch_results_user_path ON learning_path_branch_results(user_id, path_id);
