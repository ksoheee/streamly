CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE broadcasts (
    id              BIGSERIAL PRIMARY KEY,
    owner_id        BIGINT       NOT NULL REFERENCES users (id),
    title           VARCHAR(100) NOT NULL,
    description     VARCHAR(1000),
    status          VARCHAR(10)  NOT NULL,
    stream_path     VARCHAR(64)  NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ
);

CREATE INDEX idx_broadcasts_status ON broadcasts (status);
