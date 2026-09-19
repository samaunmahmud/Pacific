-- Multi-seller marketplace: sellers, per-seller orders, commission ledger, deals, wishlist, Q&A, seller ratings.

CREATE TABLE settings (
    setting_key   VARCHAR(60)  NOT NULL PRIMARY KEY,
    setting_value VARCHAR(200) NOT NULL
);
INSERT INTO settings (setting_key, setting_value) VALUES ('commission.default_percent', '10.00');

-- A seller is a normal customer account that has applied to sell (one profile per user).
CREATE TABLE seller_profiles (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    store_name          VARCHAR(80)   NOT NULL,
    slug                VARCHAR(100)  NOT NULL,
    description         VARCHAR(1000) NULL,
    status              VARCHAR(12)   NOT NULL,
    status_note         VARCHAR(300)  NULL,
    commission_override DECIMAL(5,2)  NULL,
    rating_avg          DECIMAL(3,2)  NOT NULL DEFAULT 0,
    rating_count        INT           NOT NULL DEFAULT 0,
    created_at          DATETIME(6)   NOT NULL,
    approved_at         DATETIME(6)   NULL,
    updated_at          DATETIME(6)   NULL,
    CONSTRAINT uq_seller_user UNIQUE (user_id),
    CONSTRAINT uq_seller_slug UNIQUE (slug),
    CONSTRAINT uq_seller_store_name UNIQUE (store_name),
    CONSTRAINT fk_seller_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_seller_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_seller_commission CHECK (commission_override IS NULL OR (commission_override >= 0 AND commission_override <= 100))
);

-- Products: seller_id NULL means "Sold by Pacific" (the house store run by admins).
ALTER TABLE products ADD COLUMN seller_id BIGINT NULL;
ALTER TABLE products ADD COLUMN list_price DECIMAL(10,2) NULL;
ALTER TABLE products ADD COLUMN discount_percent INT NOT NULL DEFAULT 0;
ALTER TABLE products ADD CONSTRAINT fk_products_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles (id);
CREATE INDEX idx_products_seller ON products (seller_id);
CREATE INDEX idx_products_discount ON products (discount_percent);

-- Checkout is split into one order per seller; checkout_ref groups the orders of one checkout.
ALTER TABLE orders ADD COLUMN seller_id BIGINT NULL;
ALTER TABLE orders ADD COLUMN checkout_ref VARCHAR(36) NULL;
ALTER TABLE orders ADD COLUMN commission_rate DECIMAL(5,2) NULL;
ALTER TABLE orders ADD CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles (id);
CREATE INDEX idx_orders_seller_created ON orders (seller_id, created_at);
CREATE INDEX idx_orders_checkout_ref ON orders (checkout_ref);

-- Bookkeeping only: nothing here moves real money.
CREATE TABLE ledger_entries (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    seller_id  BIGINT        NOT NULL,
    order_id   BIGINT        NULL,
    entry_type VARCHAR(12)   NOT NULL,
    amount     DECIMAL(10,2) NOT NULL,
    note       VARCHAR(200)  NULL,
    created_at DATETIME(6)   NOT NULL,
    CONSTRAINT fk_ledger_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles (id),
    CONSTRAINT fk_ledger_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_ledger_type CHECK (entry_type IN ('SALE', 'COMMISSION', 'PAYOUT'))
);
CREATE INDEX idx_ledger_seller_created ON ledger_entries (seller_id, created_at);

CREATE TABLE seller_reviews (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    seller_id  BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    rating     INT          NOT NULL,
    comment    VARCHAR(500) NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NULL,
    CONSTRAINT uq_seller_review UNIQUE (seller_id, user_id),
    CONSTRAINT fk_sr_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_sr_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_sr_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE wishlist_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_wishlist UNIQUE (user_id, product_id),
    CONSTRAINT fk_wl_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_wl_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE TABLE questions (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    text       VARCHAR(300) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_q_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_q_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_questions_product ON questions (product_id, created_at);

CREATE TABLE answers (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    text        VARCHAR(500) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_a_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    CONSTRAINT fk_a_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_answers_question ON answers (question_id, created_at);
