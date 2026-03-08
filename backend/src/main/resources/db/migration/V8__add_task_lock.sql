-- V8__add_task_lock.sql

ALTER TABLE user_task_completions ADD COLUMN is_locked BOOLEAN NOT NULL DEFAULT FALSE;
