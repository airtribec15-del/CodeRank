CREATE TABLE IF NOT EXISTS companies (
                                         id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                         name    VARCHAR(150) NOT NULL,
                                         CONSTRAINT uq_companies_name UNIQUE (name)
);