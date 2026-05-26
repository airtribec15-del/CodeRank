-- Tracks whether a specific user has solved a specific problem.
-- Populated by Problem Service's Kafka consumer when Result Processor
-- publishes a state-update-events event with verdict = ACCEPTED.
CREATE TABLE IF NOT EXISTS user_problem_state (
                                                  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                                  user_id     UUID        NOT NULL,
                                                  problem_id  UUID        NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
                                                  is_solved   BOOLEAN     NOT NULL DEFAULT false,
                                                  solved_at   TIMESTAMPTZ,
                                                  UNIQUE (user_id, problem_id)
);

CREATE INDEX IF NOT EXISTS idx_user_problem_state_user    ON user_problem_state(user_id);
CREATE INDEX IF NOT EXISTS idx_user_problem_state_problem ON user_problem_state(problem_id);