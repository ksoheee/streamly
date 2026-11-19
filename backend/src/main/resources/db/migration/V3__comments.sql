CREATE TABLE comments (
    id           BIGSERIAL PRIMARY KEY,
    broadcast_id BIGINT       NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL REFERENCES users (id),
    content      VARCHAR(500) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ
);

CREATE INDEX idx_comments_broadcast ON comments (broadcast_id, id);
