CREATE TABLE IF NOT EXISTS users (
                                     id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     username      VARCHAR(50)  NOT NULL,
                                     email         VARCHAR(255) NOT NULL,
                                     password_hash VARCHAR(255) NOT NULL,
                                     role          VARCHAR(20)  NOT NULL DEFAULT 'ROLE_USER',
                                     created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                     CONSTRAINT uq_users_username UNIQUE (username),
                                     CONSTRAINT uq_users_email    UNIQUE (email)
);