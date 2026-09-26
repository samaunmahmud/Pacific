-- Delivery choices and dates. Each seller can set their own free-delivery threshold (null = the shop's default) and
-- how many business days they take to dispatch.
ALTER TABLE seller_profiles ADD COLUMN free_delivery_threshold DECIMAL(10, 2) NULL;
ALTER TABLE seller_profiles ADD COLUMN dispatch_days INT NOT NULL DEFAULT 1;

-- "Save for later": kept in the cart but left out of totals and checkout.
ALTER TABLE cart_items ADD COLUMN saved_for_later BOOLEAN NOT NULL DEFAULT FALSE;

-- The delivery the customer chose for each order, and the dates promised at checkout.
ALTER TABLE orders ADD COLUMN delivery_option VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE orders ADD COLUMN delivery_from DATE NULL;
ALTER TABLE orders ADD COLUMN delivery_to DATE NULL;
