-- One-time MySQL setup for local development. Run as a MySQL admin, e.g.:
--   mysql -uroot -p -h127.0.0.1 -P3307 < backend/src/main/resources/db/setup-mysql.sql
-- Change the password below, then use the same value for DB_PASSWORD.

CREATE DATABASE IF NOT EXISTS pacific_marketplace
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'pacific'@'localhost' IDENTIFIED BY 'change-me-please';
GRANT ALL PRIVILEGES ON pacific_marketplace.* TO 'pacific'@'localhost';
FLUSH PRIVILEGES;
