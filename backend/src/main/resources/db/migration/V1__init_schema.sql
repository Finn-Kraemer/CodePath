-- Users Table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
    display_name VARCHAR(100),
    total_points INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Modules Table
CREATE TABLE modules (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(50) UNIQUE NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    icon_emoji VARCHAR(10),
    sort_order INT DEFAULT 0,
    is_unlocked BOOLEAN DEFAULT FALSE
);

-- Tasks Table
CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    module_id BIGINT NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    slug VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    type VARCHAR(30) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    points INT NOT NULL,
    config JSONB,
    sort_order INT DEFAULT 0,
    UNIQUE (module_id, slug)
);

-- User Task Completions
CREATE TABLE user_task_completions (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    completed_at TIMESTAMP DEFAULT NOW(),
    points_awarded INT NOT NULL,
    PRIMARY KEY (user_id, task_id)
);

-- Practice Submissions
CREATE TABLE practice_submissions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMP DEFAULT NOW(),
    reviewed_at TIMESTAMP,
    reviewer_id BIGINT REFERENCES users(id)
);
