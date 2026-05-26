package com.coderank.common.event;

import com.coderank.common.enums.Verdict;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by Result Processor to {@code state-update-events}.
 * Consumed by Problem Service to update the user_problem_state table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateUpdateEvent {

    /** The execution job ID (matches jobId on Submission). */
    private UUID jobId;

    /** The submission that was evaluated. */
    private UUID submissionId;

    /** The user who submitted the code. */
    private UUID userId;

    /** The problem that was submitted against. */
    private UUID problemId;

    /** Final verdict from the execution engine. */
    private Verdict verdict;

    /** Timestamp when the result was processed. */
    private Instant completedAt;
}