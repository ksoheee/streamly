CREATE TABLE recordings (
    id               BIGSERIAL PRIMARY KEY,
    broadcast_id     BIGINT        NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    file_path        VARCHAR(500)  NOT NULL UNIQUE,
    duration_seconds DOUBLE PRECISION NOT NULL,
    size_bytes       BIGINT        NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_recordings_broadcast ON recordings (broadcast_id);
