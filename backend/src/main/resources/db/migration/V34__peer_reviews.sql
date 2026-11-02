-- Peer-Review für Praxisabgaben: Schüler bewerten anonym Abgaben anderer nach derselben Rubrik.
-- Bringt keine Punkte; die Lehrkraft sieht die Bewertungen als Hilfe beim eigenen Bewerten.
CREATE TABLE peer_reviews (
    id                 BIGSERIAL PRIMARY KEY,
    practice_review_id BIGINT    NOT NULL REFERENCES practice_reviews (id) ON DELETE CASCADE,
    reviewer_id        BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    task_id            BIGINT    NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    scores             JSONB,
    comment            TEXT,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at       TIMESTAMP,
    CONSTRAINT ux_peer_reviews UNIQUE (practice_review_id, reviewer_id)
);

CREATE INDEX ix_peer_reviews_reviewer_task ON peer_reviews (reviewer_id, task_id);
