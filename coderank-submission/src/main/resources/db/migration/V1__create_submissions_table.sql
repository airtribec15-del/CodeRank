-- Submission Service schema
-- Tracks every RUN and SUBMIT request and its execution outcome.

CREATE TABLE IF NOT EXISTS submissions
(
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL,
    problem_id         UUID,                                        -- NULL for RUN (ad-hoc)
    job_id             UUID         NOT NULL UNIQUE,               -- Kafka correlation key
    language           VARCHAR(15)  NOT NULL,
    submission_type    VARCHAR(10)  NOT NULL CHECK (submission_type IN ('RUN', 'SUBMIT')),
    source_code        TEXT         NOT NULL,
    stdin_input        TEXT,                                        -- populated for RUN only
    stdout             TEXT,
    stderr             TEXT,
    exit_code          INT,
    execution_time_ms  BIGINT,
    status             VARCHAR(20)  NOT NULL DEFAULT 'QUEUED'
                           CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','TIMED_OUT')),
    verdict            VARCHAR(25)  NOT NULL DEFAULT 'PENDING'
                           CHECK (verdict IN ('ACCEPTED','WRONG_ANSWER','TIME_LIMIT_EXCEEDED',
                                              'RUNTIME_ERROR','COMPILATION_ERROR','PENDING')),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_submissions_user_id    ON submissions (user_id);
CREATE INDEX IF NOT EXISTS idx_submissions_problem_id ON submissions (problem_id);
CREATE INDEX IF NOT EXISTS idx_submissions_status     ON submissions (status);
CREATE INDEX IF NOT EXISTS idx_submissions_job_id     ON submissions (job_id);
