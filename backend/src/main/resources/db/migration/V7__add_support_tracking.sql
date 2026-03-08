-- V7__add_support_tracking.sql

ALTER TABLE user_task_completions ADD COLUMN support_used BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE practice_submissions ADD COLUMN support_used BOOLEAN NOT NULL DEFAULT FALSE;
