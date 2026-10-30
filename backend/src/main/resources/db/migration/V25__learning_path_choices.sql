CREATE TABLE learning_path_choices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    path_id BIGINT NOT NULL REFERENCES learning_paths (id) ON DELETE CASCADE,
    node_id VARCHAR(40) NOT NULL,
    chosen_edge_to VARCHAR(40) NOT NULL,
    chosen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    chosen_by VARCHAR(60) NOT NULL,
    UNIQUE (user_id, path_id, node_id)
);

CREATE INDEX idx_learning_path_choices_user ON learning_path_choices (user_id);
CREATE INDEX idx_learning_path_choices_path ON learning_path_choices (path_id);
