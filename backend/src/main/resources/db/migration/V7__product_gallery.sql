-- Extra product photos after the main one (products.image_url). Up to 7 addresses of at most 500 characters, one per
-- line. Kept on the product row so catalogue pages don't need another query per product.
ALTER TABLE products ADD COLUMN more_images VARCHAR(4000) NULL;
