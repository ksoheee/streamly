CREATE TABLE chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    broadcast_id BIGINT       NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    user_id      BIGINT       NOT NULL REFERENCES users (id),
    content      VARCHAR(200) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_chat_messages_broadcast ON chat_messages (broadcast_id, id);
