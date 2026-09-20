-- Order lifecycle: tracking details, a timeline of what happened to each order, and a record of emails we sent.

ALTER TABLE orders ADD COLUMN tracking_carrier VARCHAR(60) NULL;
ALTER TABLE orders ADD COLUMN tracking_number  VARCHAR(80) NULL;
ALTER TABLE orders ADD COLUMN shipped_at       DATETIME(6) NULL;
ALTER TABLE orders ADD COLUMN delivered_at     DATETIME(6) NULL;

UPDATE orders SET shipped_at   = COALESCE(updated_at, created_at) WHERE status IN ('SHIPPED', 'DELIVERED');
UPDATE orders SET delivered_at = COALESCE(updated_at, created_at) WHERE status = 'DELIVERED';

CREATE TABLE order_events (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT       NOT NULL,
    event_type VARCHAR(30)  NOT NULL,
    note       VARCHAR(300) NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_order_events_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
CREATE INDEX idx_order_events_order ON order_events (order_id, created_at);

-- Orders that existed before this migration get the events we can work out from their dates.
INSERT INTO order_events (order_id, event_type, note, created_at)
    SELECT id, 'PLACED', NULL, created_at FROM orders WHERE status <> 'AWAITING_PAYMENT';
INSERT INTO order_events (order_id, event_type, note, created_at)
    SELECT id, 'SHIPPED', NULL, shipped_at FROM orders WHERE shipped_at IS NOT NULL;
INSERT INTO order_events (order_id, event_type, note, created_at)
    SELECT id, 'DELIVERED', NULL, delivered_at FROM orders WHERE delivered_at IS NOT NULL;
INSERT INTO order_events (order_id, event_type, note, created_at)
    SELECT id, 'CANCELLED', NULL, COALESCE(updated_at, created_at) FROM orders WHERE status = 'CANCELLED';

-- Every email the shop sends (or would send, when no mail server is configured), for support and for testing.
CREATE TABLE sent_emails (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    to_address VARCHAR(255)  NOT NULL,
    subject    VARCHAR(200)  NOT NULL,
    kind       VARCHAR(30)   NOT NULL,
    body       VARCHAR(4000) NOT NULL,
    status     VARCHAR(10)   NOT NULL,
    error      VARCHAR(300)  NULL,
    created_at DATETIME(6)   NOT NULL
);
CREATE INDEX idx_sent_emails_created ON sent_emails (created_at);
CREATE INDEX idx_sent_emails_to ON sent_emails (to_address);
