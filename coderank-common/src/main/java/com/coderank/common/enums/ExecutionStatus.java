package com.coderank.common.enums;

/**
 * Infrastructure-level execution status of a code submission job.
 *
 * <p>This is distinct from {@link Verdict}:
 * <ul>
 *   <li>{@code ExecutionStatus} describes the infrastructure outcome
 *       (did the container run? did Kafka deliver? did the system time out?)</li>
 *   <li>{@link Verdict} describes the correctness outcome
 *       (did the code produce the right answer?)</li>
 * </ul>
 *
 * <p>A job can have status=COMPLETED but verdict=WRONG_ANSWER.
 * A job can have status=TIMEDOUT and verdict=TIME_LIMIT_EXCEEDED.
 */
public enum ExecutionStatus {

    /** Job has been accepted and is waiting in the Kafka queue. */
    QUEUED,

    /** Execution Service has picked up the job and is running it in Docker. */
    RUNNING,

    /** Container exited with code 0 — code ran to completion without system errors.
     *  Does NOT mean the answer is correct — see Verdict for that. */
    COMPLETED,

    /** Container exited with non-zero code, crashed, or encountered a runtime error. */
    FAILED,

    /**
     * Container was killed because it exceeded the configured time limit.
     * Distinct from FAILED so consumers can distinguish TLE from RE
     * without parsing stderr.
     */
    TIMEDOUT
}