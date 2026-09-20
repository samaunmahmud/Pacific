-- Returns and refunds after delivery.

-- The return window is fixed when an order is delivered, so a later policy change doesn't move a customer's deadline.
ALTER TABLE orders ADD COLUMN return_deadline DATETIME(6) NULL;
UPDATE orders SET return_deadline = TIMESTAMPADD(DAY, 30, delivered_at) WHERE delivered_at IS NOT NULL;

CREATE TABLE return_requests (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT        NOT NULL,
    status        VARCHAR(12)   NOT NULL,
    reason        VARCHAR(20)   NOT NULL,
    comment       VARCHAR(500)  NULL,
    seller_note   VARCHAR(300)  NULL,
    refund_amount DECIMAL(10,2) NULL,
    restocked     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NULL,
    resolved_at   DATETIME(6)   NULL,
    CONSTRAINT fk_returns_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_returns_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'REFUNDED', 'CANCELLED')),
    CONSTRAINT ck_returns_reason CHECK (reason IN ('DAMAGED', 'NOT_AS_DESCRIBED', 'WRONG_ITEM', 'NO_LONGER_NEEDED', 'OTHER')),
    CONSTRAINT ck_returns_refund CHECK (refund_amount IS NULL OR refund_amount > 0)
);
CREATE INDEX idx_returns_order ON return_requests (order_id);
CREATE INDEX idx_returns_status_created ON return_requests (status, created_at);

CREATE TABLE return_items (
    id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    return_id     BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    quantity      INT    NOT NULL,
    CONSTRAINT fk_return_items_return FOREIGN KEY (return_id) REFERENCES return_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_return_items_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT ck_return_items_qty CHECK (quantity > 0)
);
CREATE INDEX idx_return_items_return ON return_items (return_id);

-- The ledger learns about refunds: the seller gives the money back, and the marketplace gives back its commission on it.
ALTER TABLE ledger_entries DROP CONSTRAINT ck_ledger_type;
ALTER TABLE ledger_entries MODIFY entry_type VARCHAR(20) NOT NULL;
ALTER TABLE ledger_entries ADD CONSTRAINT ck_ledger_type
    CHECK (entry_type IN ('SALE', 'COMMISSION', 'PAYOUT', 'REFUND', 'COMMISSION_REFUND'));
