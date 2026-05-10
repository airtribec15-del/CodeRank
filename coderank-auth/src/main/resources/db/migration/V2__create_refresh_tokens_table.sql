CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                              user_id     UUID         NOT NULL,
                                              token_hash  VARCHAR(255) NOT NULL,
                                              expires_at  TIMESTAMPTZ  NOT NULL,
                                              created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                              revoked     BOOLEAN      NOT NULL DEFAULT false,
                                              device_info VARCHAR(255),
                                              CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
                                              CONSTRAINT fk_refresh_tokens_user
                                                  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);