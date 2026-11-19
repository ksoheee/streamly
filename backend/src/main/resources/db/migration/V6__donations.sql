-- 도네이션. 주문(PENDING)을 먼저 만들고, 토스 결제 승인이 확인되면 PAID 로 바꾼다.
CREATE TABLE donations (
    id           BIGSERIAL PRIMARY KEY,
    broadcast_id BIGINT       NOT NULL REFERENCES broadcasts (id) ON DELETE CASCADE,
    donor_id     BIGINT       NOT NULL REFERENCES users (id),
    recipient_id BIGINT       NOT NULL REFERENCES users (id),
    order_id     VARCHAR(64)  NOT NULL UNIQUE,
    amount       BIGINT       NOT NULL,
    message      VARCHAR(100),
    status       VARCHAR(10)  NOT NULL,
    payment_key  VARCHAR(200) UNIQUE,
    created_at   TIMESTAMPTZ  NOT NULL,
    paid_at      TIMESTAMPTZ
);

CREATE INDEX idx_donations_broadcast ON donations (broadcast_id, status);
