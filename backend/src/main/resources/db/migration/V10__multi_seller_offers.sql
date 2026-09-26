-- Several sellers can sell the same product. The first listing is the product's catalog page (its reviews and
-- questions live there); other sellers' listings ("offers") point at it with group_id. The cart, orders and stock keep
-- working per listing.
ALTER TABLE products ADD COLUMN group_id BIGINT NULL;
ALTER TABLE products ADD CONSTRAINT fk_products_group FOREIGN KEY (group_id) REFERENCES products (id);
CREATE INDEX idx_products_group ON products (group_id);

-- NEW, USED_LIKE_NEW, USED_GOOD or USED_ACCEPTABLE. Only new offers compete for the buy box against new ones.
ALTER TABLE products ADD COLUMN item_condition VARCHAR(20) NOT NULL DEFAULT 'NEW';

-- Kept on the catalog page by BuyBox.refresh, so search can filter and sort by the price shoppers actually pay:
-- the buy-box winner's price, and how many listings are on sale.
ALTER TABLE products ADD COLUMN box_price DECIMAL(10, 2) NULL;
ALTER TABLE products ADD COLUMN offer_count INT NOT NULL DEFAULT 1;
UPDATE products SET box_price = price;
