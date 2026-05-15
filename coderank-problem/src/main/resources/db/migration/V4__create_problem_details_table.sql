-- Examples shown to users alongside the problem description
CREATE TABLE IF NOT EXISTS problem_examples (
                                                id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                problem_id      UUID NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
                                                input_text      TEXT NOT NULL,
                                                output_text     TEXT NOT NULL,
                                                explanation     TEXT,
                                                order_index     INT  NOT NULL DEFAULT 0
);

-- Test cases used by the Execution Service (hidden from users)
CREATE TABLE IF NOT EXISTS test_cases (
                                          id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
                                          problem_id  UUID    NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
                                          input       TEXT    NOT NULL,
                                          expected    TEXT    NOT NULL,
                                          is_sample   BOOLEAN NOT NULL DEFAULT false,
                                          order_index INT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_test_cases_problem_id ON test_cases(problem_id);
CREATE INDEX idx_examples_problem_id   ON problem_examples(problem_id);