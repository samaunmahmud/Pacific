-- Private messages between a buyer and a seller's store. One conversation per buyer and store; each message may say
-- which product or order it's about.
CREATE TABLE conversations (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_id        BIGINT       NOT NULL,
    seller_id       BIGINT       NOT NULL,
    -- messages the other side hasn't read yet, kept per side so inbox lists need no counting
    buyer_unread    INT          NOT NULL DEFAULT 0,
    seller_unread   INT          NOT NULL DEFAULT 0,
    last_message_at DATETIME(6)  NOT NULL,
    last_preview    VARCHAR(140) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_conv_buyer FOREIGN KEY (buyer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_conv_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles (id) ON DELETE CASCADE,
    CONSTRAINT uq_conv_buyer_seller UNIQUE (buyer_id, seller_id)
);
CREATE INDEX idx_conv_buyer_last ON conversations (buyer_id, last_message_at);
CREATE INDEX idx_conv_seller_last ON conversations (seller_id, last_message_at);

CREATE TABLE messages (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT        NOT NULL,
    sender_id       BIGINT        NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    product_id      BIGINT        NULL,
    order_id        BIGINT        NULL,
    created_at      DATETIME(6)   NOT NULL,
    CONSTRAINT fk_msg_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
    CONSTRAINT fk_msg_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL
);
CREATE INDEX idx_msg_conversation ON messages (conversation_id, id);
