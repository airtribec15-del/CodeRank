CREATE TABLE IF NOT EXISTS topics (
                                      id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      name    VARCHAR(100) NOT NULL,
                                      CONSTRAINT uq_topics_name UNIQUE (name)
);