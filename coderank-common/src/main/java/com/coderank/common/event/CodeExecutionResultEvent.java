package com.coderank.common.event;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event published by Execution Service to {@code code.execution.results}.
 * Consumed by Result Processor.
 *
 * <p>{@code verdict} is non-null only for SUBMIT-type jobs (where {@code problemId}
 * is present). For RUN-type playground jobs, {@code verdict} will be null and
 * {@code stdout}/{@code stderr} carry the raw execution output.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecutionResultEvent {

    /** Matches the jobId from the originating {@link CodeExecutionRequestEvent}. */
    private UUID jobId;

    /**
     * The submission record to update in Submission Service DB.
     * Null for RUN-type playground jobs.
     */
    private UUID submissionId;

    /** The user who submitted the code — forwarded for downstream state updates. */
    private UUID userId;

    /**
     * The problem this submission was evaluated against.
     * Non-null for SUBMIT-type jobs; null for RUN-type jobs.
     * Required by Result Processor to publish {@code state-update-events}.
     */
    private UUID problemId;

    /**
     * Final verdict for SUBMIT-type jobs.
     * Null for RUN-type playground executions.
     */
    private Verdict verdict;

    /**
     * Infrastructure-level execution status (COMPLETED, FAILED, TIMEOUT, etc.).
     * Distinct from {@code verdict}: a job can COMPLETE execution but yield WRONG_ANSWER.
     */
    private ExecutionStatus status;

    /** Combined stdout from all test case runs (truncated to 64KB if necessary). */
    private String stdout;

    /** Combined stderr / compiler errors (truncated to 16KB if necessary). */
    private String stderr;

    /** Exit code of the last executed process (-1 for internal failures). */
    private Integer exitCode;

    /** Total wall-clock execution time across all test cases in milliseconds. */
    private Long executionTimeMs;

    /** Timestamp when execution completed. */
    private Instant completedAt;
}