-- Email verification at sign-up. New customers confirm their address before ordering, selling or posting questions.

ALTER TABLE users ADD COLUMN email_verified_at DATETIME(6) NULL;

-- Everyone who signed up before this change keeps doing what they could: their addresses count as confirmed.
UPDATE users SET email_verified_at = created_at WHERE email IS NOT NULL;

-- Like password reset links: only a hash of the token is stored, and each link works once.
CREATE TABLE email_verification_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_verify_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_verify_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_verify_user_created ON email_verification_tokens (user_id, created_at);
