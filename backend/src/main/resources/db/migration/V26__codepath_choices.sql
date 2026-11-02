CREATE TABLE codepath_choices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    path_id BIGINT NOT NULL REFERENCES codepaths (id) ON DELETE CASCADE,
    node_id VARCHAR(40) NOT NULL,
    chosen_edge_to VARCHAR(40) NOT NULL,
    chosen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    chosen_by VARCHAR(60) NOT NULL,
    UNIQUE (user_id, path_id, node_id)
);

CREATE INDEX idx_codepath_choices_user ON codepath_choices (user_id);
CREATE INDEX idx_codepath_choices_path ON codepath_choices (path_id);
