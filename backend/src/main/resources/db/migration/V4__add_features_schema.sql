-- V4__add_features_schema.sql

-- Update user_task_completions for progress tracking
ALTER TABLE user_task_completions ALTER COLUMN completed_at DROP NOT NULL;
ALTER TABLE user_task_completions ALTER COLUMN points_awarded SET DEFAULT 0;
ALTER TABLE user_task_completions ADD COLUMN is_completed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_task_completions ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE user_task_completions ADD COLUMN time_spent_seconds BIGINT NOT NULL DEFAULT 0;

-- Mark all existing completions as completed
UPDATE user_task_completions SET is_completed = TRUE;

-- Update practice_submissions for rejection feedback
ALTER TABLE practice_submissions ADD COLUMN admin_comment TEXT;

-- Create Task Comments table
CREATE TABLE task_comments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Create Global Announcements table
CREATE TABLE global_announcements (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW(),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);
