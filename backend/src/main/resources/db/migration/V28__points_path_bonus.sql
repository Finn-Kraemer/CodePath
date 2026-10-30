ALTER TABLE points_entries
    ADD COLUMN path_id   BIGINT      REFERENCES learning_paths (id) ON DELETE SET NULL,
    ADD COLUMN node_id   VARCHAR(80),
    ADD COLUMN cancelled BOOLEAN     NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX ux_points_path_bonus
    ON points_entries (user_id, path_id, node_id)
    WHERE reason = 'PATH_BONUS' AND NOT cancelled;
