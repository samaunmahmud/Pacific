-- Account security and account basics: password reset, session revocation on password change, saved addresses.

-- Bumped whenever the password changes; sign-in tokens carry it, so older sessions stop working.
ALTER TABLE users ADD COLUMN password_version INT NOT NULL DEFAULT 0;

-- Only a hash of the reset token is stored, so a copy of the database can't be used to take over accounts.
CREATE TABLE password_reset_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_reset_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_reset_user_created ON password_reset_tokens (user_id, created_at);

CREATE TABLE user_addresses (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(120) NOT NULL,
    line1      VARCHAR(160) NOT NULL,
    line2      VARCHAR(160) NULL,
    city       VARCHAR(80)  NOT NULL,
    postcode   VARCHAR(20)  NOT NULL,
    country    VARCHAR(80)  NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NULL,
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_addresses_user ON user_addresses (user_id);
