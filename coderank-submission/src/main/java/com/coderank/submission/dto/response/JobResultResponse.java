package com.coderank.submission.dto.response;

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
 * Lightweight response for the Redis-cache-first polling endpoint
 * {@code GET /api/v1/submissions/{id}/result}.
 *
 * <p>Returned from the cache when the job is in-flight (QUEUED/RUNNING)
 * or terminal (COMPLETED/FAILED/TIMEDOUT). The {@code source} field
 * indicates whether the value came from Redis ({@code "cache"})
 * or the database ({@code "db"}) on a cache miss.</p>
 *
 * <p>Clients should poll until {@code status} is one of:
 * {@code COMPLETED}, {@code FAILED}, {@code TIMEDOUT}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResultResponse {

    /** The Kafka correlation ID used as the Redis cache key. */
    private UUID jobId;

    /** The submission record ID in the Submission Service DB. */
    private UUID submissionId;

    /**
     * Infrastructure-level execution status.
     * In-flight: {@code QUEUED}, {@code RUNNING}.
     * Terminal: {@code COMPLETED}, {@code FAILED}, {@code TIMEDOUT}.
     */
    private ExecutionStatus status;

    /**
     * Correctness verdict — non-null only for SUBMIT-type jobs once terminal.
     * Null for RUN-type (ad-hoc) jobs and in-flight SUBMIT-type jobs.
     */
    private Verdict verdict;

    /** Total wall-clock execution time in milliseconds — null while in-flight. */
    private Long executionTimeMs;

    /**
     * Timestamp when execution completed — null while in-flight.
     * Populated from DB on cache miss; null on cache hit (not stored in Redis).
     */
    @JsonSerialize(using = InstantSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant completedAt;

    /**
     * Indicates whether this response was served from Redis ({@code "cache"})
     * or the database ({@code "db"}) on a cache miss.
     * Useful for client-side debugging and monitoring.
     */
    private String source;
}