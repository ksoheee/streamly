-- 방송 목록 썸네일. 송출 화면을 주기적으로 캡처한 작은 JPEG. 목록 조회에 이미지가 딸려오지 않게 따로 둔다.
CREATE TABLE broadcast_thumbnails (
    broadcast_id BIGINT      PRIMARY KEY REFERENCES broadcasts (id) ON DELETE CASCADE,
    image        BYTEA       NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);

ALTER TABLE broadcasts ADD COLUMN thumbnail_updated_at TIMESTAMPTZ;
