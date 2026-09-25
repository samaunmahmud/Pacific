-- Promotions, each funded by the store that runs it (a seller's store, or Pacific's own for house products). They all
-- lower the price paid per unit, so orders, refunds and seller earnings need nothing special.

-- Lightning Deals: a deal price for a few hours on a limited number of units.
CREATE TABLE lightning_deals (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT        NOT NULL,
    deal_price DECIMAL(10,2) NOT NULL,
    quantity   INT           NOT NULL,
    claimed    INT           NOT NULL DEFAULT 0,
    starts_at  DATETIME(6)   NOT NULL,
    ends_at    DATETIME(6)   NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    CONSTRAINT fk_deals_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX idx_deals_product_window ON lightning_deals (product_id, starts_at, ends_at);
CREATE INDEX idx_deals_window ON lightning_deals (starts_at, ends_at);

-- Coupons: a percentage off one listing, clipped by a shopper, used once per customer, up to a budget of uses.
CREATE TABLE coupons (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT      NOT NULL,
    percent_off INT         NOT NULL,
    budget      INT         NOT NULL,
    used        INT         NOT NULL DEFAULT 0,
    ends_at     DATETIME(6) NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_coupons_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX idx_coupons_product ON coupons (product_id);

CREATE TABLE coupon_clips (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    coupon_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    order_id   BIGINT      NULL,
    clipped_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_clips_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id) ON DELETE CASCADE,
    CONSTRAINT fk_clips_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_clip UNIQUE (coupon_id, user_id)
);

-- Promo codes: a percentage off one store's items, typed at checkout, once per customer.
CREATE TABLE promo_codes (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(30)   NOT NULL,
    seller_id   BIGINT        NULL,
    percent_off INT           NOT NULL,
    min_spend   DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_uses    INT           NULL,
    used        INT           NOT NULL DEFAULT 0,
    ends_at     DATETIME(6)   NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)   NOT NULL,
    CONSTRAINT fk_promo_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles (id),
    CONSTRAINT uq_promo_code UNIQUE (code)
);

CREATE TABLE promo_redemptions (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    promo_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    order_id   BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_redemption_promo FOREIGN KEY (promo_id) REFERENCES promo_codes (id) ON DELETE CASCADE,
    CONSTRAINT fk_redemption_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_redemption UNIQUE (promo_id, user_id)
);

-- What each order line was sold at before promotions, and which ones it used (to release them on cancellation).
ALTER TABLE order_items ADD COLUMN list_unit_price DECIMAL(10,2) NULL;
ALTER TABLE order_items ADD COLUMN deal_id BIGINT NULL;
ALTER TABLE order_items ADD COLUMN coupon_id BIGINT NULL;
ALTER TABLE order_items ADD COLUMN promo_id BIGINT NULL;
ALTER TABLE order_items ADD COLUMN promotion VARCHAR(60) NULL;
