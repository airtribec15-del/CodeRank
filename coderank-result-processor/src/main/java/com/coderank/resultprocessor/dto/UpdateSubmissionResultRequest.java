package com.coderank.resultprocessor.dto;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body sent by Result Processor to Submission Service's internal endpoint:
 * {@code PATCH /api/v1/internal/submissions/result}
 *
 * <p>Maps directly to what {@code InternalSubmissionController} expects.
 * All fields mirror the {@code CodeExecutionResultEvent} payload but are
 * typed for the Submission Service's domain (e.g. {@link Verdict} enum).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubmissionResultRequest {

    private UUID jobId;
    private UUID submissionId;

    /** Infrastructure outcome — did the container run successfully? */
    private ExecutionStatus status;

    /** Correctness verdict — was the code correct? Null for RUN-type jobs. */
    private Verdict verdict;

    private String stdout;
    private String stderr;
    private Integer exitCode;
    private Long executionTimeMs;

    @JsonSerialize(using = InstantSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant completedAt;
}