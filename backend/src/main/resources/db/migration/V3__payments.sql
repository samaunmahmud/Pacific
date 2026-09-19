-- Online card payments (one payment per checkout, covering every seller's order in that checkout).

CREATE TABLE payments (
    id                   BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    checkout_ref         VARCHAR(36)   NOT NULL,
    user_id              BIGINT        NOT NULL,
    provider             VARCHAR(12)   NOT NULL,
    provider_session_id  VARCHAR(200)  NULL,
    provider_payment_ref VARCHAR(200)  NULL,
    checkout_url         VARCHAR(1000) NULL,
    amount               DECIMAL(10,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    status               VARCHAR(12)   NOT NULL,
    refunded_amount      DECIMAL(10,2) NOT NULL DEFAULT 0,
    expires_at           DATETIME(6)   NOT NULL,
    created_at           DATETIME(6)   NOT NULL,
    paid_at              DATETIME(6)   NULL,
    updated_at           DATETIME(6)   NULL,
    CONSTRAINT uq_payments_ref UNIQUE (checkout_ref),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_payments_provider CHECK (provider IN ('STRIPE', 'SIMULATOR')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_payments_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= amount)
);
CREATE INDEX idx_payments_status_expires ON payments (status, expires_at);
CREATE INDEX idx_payments_session ON payments (provider_session_id);
