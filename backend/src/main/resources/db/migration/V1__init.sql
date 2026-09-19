-- Pacific marketplace schema. Kept to SQL that runs on MySQL 8 (production) and H2 in MySQL mode (tests).

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(190) NULL,
    username      VARCHAR(60)  NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE categories (
    id   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80)  NOT NULL,
    slug VARCHAR(100) NOT NULL,
    CONSTRAINT uq_categories_name UNIQUE (name),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

CREATE TABLE products (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(160)  NOT NULL,
    description  VARCHAR(2000) NULL,
    price        DECIMAL(10,2) NOT NULL,
    stock        INT           NOT NULL DEFAULT 0,
    image_url    VARCHAR(500)  NULL,
    category_id  BIGINT        NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    rating_avg   DECIMAL(3,2)  NOT NULL DEFAULT 0,
    rating_count INT           NOT NULL DEFAULT 0,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NULL,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT ck_products_price CHECK (price >= 0),
    CONSTRAINT ck_products_stock CHECK (stock >= 0)
);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_active_created ON products (active, created_at);

CREATE TABLE cart_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT uq_cart_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_cart_quantity CHECK (quantity > 0)
);

CREATE TABLE orders (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    subtotal       DECIMAL(10,2) NOT NULL,
    shipping       DECIMAL(10,2) NOT NULL,
    total          DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(30)   NOT NULL,
    ship_name      VARCHAR(120)  NOT NULL,
    ship_line1     VARCHAR(160)  NOT NULL,
    ship_line2     VARCHAR(160)  NULL,
    ship_city      VARCHAR(80)   NOT NULL,
    ship_postcode  VARCHAR(20)   NOT NULL,
    ship_country   VARCHAR(80)   NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    product_name VARCHAR(160)  NOT NULL,
    unit_price   DECIMAL(10,2) NOT NULL,
    quantity     INT           NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX idx_order_items_product ON order_items (product_id);

CREATE TABLE reviews (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    rating          INT          NOT NULL,
    title           VARCHAR(120) NULL,
    comment         VARCHAR(1000) NOT NULL,
    image_url       VARCHAR(500) NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'VISIBLE',
    flag_reason     VARCHAR(200) NULL,
    helpful_count   INT          NOT NULL DEFAULT 0,
    unhelpful_count INT          NOT NULL DEFAULT 0,
    edited_by_admin BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NULL,
    CONSTRAINT uq_reviews_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_status CHECK (status IN ('VISIBLE', 'FLAGGED'))
);
CREATE INDEX idx_reviews_product_status ON reviews (product_id, status);
CREATE INDEX idx_reviews_status ON reviews (status);

CREATE TABLE review_votes (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    vote_type  VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_votes_review_user UNIQUE (review_id, user_id),
    CONSTRAINT fk_votes_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_votes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_votes_type CHECK (vote_type IN ('HELPFUL', 'UNHELPFUL'))
);
