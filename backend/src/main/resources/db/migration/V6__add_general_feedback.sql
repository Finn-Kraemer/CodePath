-- V6__add_general_feedback.sql
CREATE TABLE system_settings (
    key VARCHAR(100) PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);

INSERT INTO system_settings (key, value) VALUES ('feedback_enabled', 'false');

CREATE TABLE general_feedback (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    submitted_at TIMESTAMP DEFAULT NOW()
);
