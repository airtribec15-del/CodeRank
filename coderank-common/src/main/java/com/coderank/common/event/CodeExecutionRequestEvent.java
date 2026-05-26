package com.coderank.common.event;

import com.coderank.common.enums.Language;
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
 * Kafka event published by Submission Service to {@code code.execution.requests}.
 * Consumed by Execution Service.
 *
 * <p>If {@code problemId} is non-null → SUBMIT-type job: Execution Service must fetch
 * test cases from Problem Service and compute a full verdict.</p>
 * <p>If {@code problemId} is null → RUN-type job: Execution Service runs code once
 * against {@code stdinInput} only.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecutionRequestEvent {

    /** Kafka correlation ID — used as the Redis key and Kafka message key. */
    private UUID jobId;

    /** The submission record ID in the Submission Service DB. */
    private UUID submissionId;

    /** The user who submitted. */
    private UUID userId;

    /**
     * The problem this submission belongs to.
     * NULL for RUN-type (ad-hoc) jobs.
     * NON-NULL for SUBMIT-type jobs — triggers test-case fetch in Execution Service.
     * (FAULT-01 FIX)
     */
    private UUID problemId;

    /** Programming language of the submitted code. */
    private Language language;

    /** The raw source code submitted by the user. */
    private String sourceCode;

    /**
     * Custom stdin input for RUN-type jobs.
     * NULL for SUBMIT-type jobs — stdin is provided per test case by Problem Service.
     */
    private String stdinInput;

    /** Maximum allowed wall-clock execution time in seconds. */
    private int timeoutSeconds;

    /** Wall-clock time when the submission was accepted by Submission Service. */
    @JsonSerialize(using = InstantSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant submittedAt;
}