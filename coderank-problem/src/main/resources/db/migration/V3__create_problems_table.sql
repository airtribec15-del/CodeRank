CREATE TABLE IF NOT EXISTS problems (
                                        id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        title           VARCHAR(255)  NOT NULL,
                                        slug            VARCHAR(255)  NOT NULL,
                                        description     TEXT          NOT NULL,
                                        difficulty      VARCHAR(10)   NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
                                        state           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
                                        constraints     TEXT,
                                        created_by      UUID          NOT NULL,
                                        created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                        updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                        CONSTRAINT uq_problems_slug UNIQUE (slug)
);

-- Junction table: problems ↔ topics (many-to-many)
CREATE TABLE IF NOT EXISTS problem_topics (
                                              problem_id  UUID NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
                                              topic_id    UUID NOT NULL REFERENCES topics(id)   ON DELETE CASCADE,
                                              PRIMARY KEY (problem_id, topic_id)
);

-- Junction table: problems ↔ companies (many-to-many)
CREATE TABLE IF NOT EXISTS problem_companies (
                                                 problem_id  UUID NOT NULL REFERENCES problems(id)   ON DELETE CASCADE,
                                                 company_id  UUID NOT NULL REFERENCES companies(id)  ON DELETE CASCADE,
                                                 PRIMARY KEY (problem_id, company_id)
);

CREATE INDEX idx_problems_difficulty ON problems(difficulty);
CREATE INDEX idx_problems_state      ON problems(state);
CREATE INDEX idx_problems_slug       ON problems(slug);