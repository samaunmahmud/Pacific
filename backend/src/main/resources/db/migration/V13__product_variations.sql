-- Variations: product pages that are the same item in another colour, size and so on share a family and show as one
-- card in search, with a picker on the product page. Each variation is still its own catalog page (own price, stock,
-- photos, reviews and other sellers' offers), so the cart, orders and buy box are unchanged.
CREATE TABLE product_families (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- What the variations differ by, e.g. "Colour" and "Size"; dim2 is null for one dimension.
    dim1       VARCHAR(30) NOT NULL,
    dim2       VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL
);

ALTER TABLE products ADD COLUMN family_id BIGINT NULL;
ALTER TABLE products ADD CONSTRAINT fk_products_family FOREIGN KEY (family_id) REFERENCES product_families (id);
CREATE INDEX idx_products_family ON products (family_id);
ALTER TABLE products ADD COLUMN option1 VARCHAR(40) NULL;
ALTER TABLE products ADD COLUMN option2 VARCHAR(40) NULL;
-- "Colour: Red, Size: M", kept on the page and copied to other sellers' offers for it, so carts show it cheaply.
ALTER TABLE products ADD COLUMN variation VARCHAR(100) NULL;

-- Snapshotted like the product name, so an order keeps saying which variation was bought.
ALTER TABLE order_items ADD COLUMN variation VARCHAR(100) NULL;
