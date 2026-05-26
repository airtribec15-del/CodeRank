package com.coderank.resultprocessor.service;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.constants.RedisKeys;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.common.event.StateUpdateEvent;
import com.coderank.resultprocessor.client.SubmissionServiceClient;
import com.coderank.resultprocessor.dto.UpdateSubmissionResultRequest;
import com.coderank.resultprocessor.exception.NonRetryableResultException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Core business logic for the Result Processor pipeline.
 *
 * <h2>Execution Order (CRITICAL — must follow the locked flow)</h2>
 * <ol>
 *   <li>Validate the event (non-null submissionId, jobId)</li>
 *   <li>Call {@link SubmissionServiceClient#updateSubmissionResult} — persist final
 *       status and verdict to the Submission Service DB</li>
 *   <li>Cache job result in Redis: {@code job-status:{jobId}} with 24h TTL</li>
 *   <li>Publish {@link StateUpdateEvent} to {@code state-update-events} (SUBMIT-type only)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultProcessorService {

    private final SubmissionServiceClient submissionServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, StateUpdateEvent> stateUpdateKafkaTemplate;

    @Value("${result-processor.cache.job-result-ttl-hours:24}")
    private int jobResultTtlHours;

    public void process(CodeExecutionResultEvent event) {
        log.info("PROCESSING result jobId={} submissionId={} status={} verdict={}",
                event.getJobId(), event.getSubmissionId(),
                event.getStatus(), event.getVerdict());

        // ── Step 1: Validate ─────────────────────────────────────────────────
        if (event.getJobId() == null) {
            throw new NonRetryableResultException(
                    "CodeExecutionResultEvent has null jobId — cannot process");
        }
        if (event.getSubmissionId() == null) {
            throw new NonRetryableResultException(
                    "CodeExecutionResultEvent has null submissionId — jobId=" + event.getJobId());
        }

        // ── Step 2: Update Submission Service DB ─────────────────────────────
        UpdateSubmissionResultRequest updateRequest = buildUpdateRequest(event);
        submissionServiceClient.updateSubmissionResult(updateRequest);
        log.debug("Step 2 complete: Submission Service updated for jobId={}", event.getJobId());

        // ── Step 3: Cache in Redis ────────────────────────────────────────────
        cacheJobResult(event);
        log.debug("Step 3 complete: Redis cached for jobId={}", event.getJobId());

        // ── Step 4: Publish state-update-events (SUBMIT-type only) ───────────
        if (event.getProblemId() != null) {
            publishStateUpdate(event);
            log.debug("Step 4 complete: StateUpdateEvent published for problemId={}",
                    event.getProblemId());
        } else {
            log.debug("Step 4 skipped: RUN-type job (problemId is null) for jobId={}",
                    event.getJobId());
        }

        log.info("PROCESSED result jobId={} submissionId={} verdict={}",
                event.getJobId(), event.getSubmissionId(), event.getVerdict());
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private UpdateSubmissionResultRequest buildUpdateRequest(CodeExecutionResultEvent event) {
        // getVerdict() returns Verdict enum directly — assign without any conversion.
        Verdict verdict = event.getVerdict();

        return UpdateSubmissionResultRequest.builder()
                .jobId(event.getJobId())
                .submissionId(event.getSubmissionId())
                .status(event.getStatus())
                .verdict(verdict)
                .stdout(event.getStdout())
                .stderr(event.getStderr())
                .exitCode(event.getExitCode())
                .executionTimeMs(event.getExecutionTimeMs())
                .completedAt(event.getCompletedAt() != null
                        ? event.getCompletedAt()
                        : Instant.now())
                .build();
    }

    /**
     * Caches the job result as a compact string in Redis.
     * Key:   {@code job-status:{jobId}}
     * Value: {@code "STATUS:VERDICT"} e.g. {@code "COMPLETED:ACCEPTED"}
     *        For RUN-type jobs (no verdict): just {@code "COMPLETED"}
     * TTL:   24 hours
     */
    private void cacheJobResult(CodeExecutionResultEvent event) {
        String key = RedisKeys.jobStatusKey(event.getJobId().toString());
        // event.getVerdict() is a Verdict enum — call .name() here ONLY for Redis
        // string serialization (Redis stores raw strings, not enums).
        String value = event.getStatus().name() +
                (event.getVerdict() != null ? ":" + event.getVerdict().name() : "");

        redisTemplate.opsForValue().set(key, value, Duration.ofHours(jobResultTtlHours));
        log.debug("Cached Redis key={} value={} ttl={}h", key, value, jobResultTtlHours);
    }

    /**
     * Publishes a {@link StateUpdateEvent} to {@code state-update-events}.
     * Only called for SUBMIT-type jobs (problemId non-null).
     *
     * <p>Keyed by userId for partition ordering — prevents race conditions
     * on user_problem_state upserts in Problem Service.</p>
     *
     * <p><strong>FIX (ISSUE-B):</strong> Use {@code .get()} to block until the
     * broker confirms receipt. This ensures a publish failure propagates back
     * to {@link #process(CodeExecutionResultEvent)} as a thrown exception,
     * preventing the Kafka offset from being ACKed on publish failure.
     * RetryableTopic will then re-deliver and retry the full pipeline.</p>
     */
    private void publishStateUpdate(CodeExecutionResultEvent event) {
        StateUpdateEvent stateUpdate = StateUpdateEvent.builder()
                .jobId(event.getJobId())
                .submissionId(event.getSubmissionId())
                .userId(event.getUserId())
                .problemId(event.getProblemId())
                // FIX: StateUpdateEvent.verdict is typed as Verdict enum.
                // Pass event.getVerdict() directly — NO .name() conversion.
                .verdict(event.getVerdict())
                .completedAt(event.getCompletedAt() != null ? event.getCompletedAt() : Instant.now())
                .build();

        String messageKey = event.getUserId() != null
                ? event.getUserId().toString()
                : event.getSubmissionId().toString();

        try {
            var sendResult = stateUpdateKafkaTemplate
                    .send(KafkaTopics.STATE_UPDATE_EVENTS, messageKey, stateUpdate)
                    .get(); // Blocks until broker ACK — failure throws ExecutionException
            log.debug("StateUpdateEvent published partition={} offset={}",
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing StateUpdateEvent for jobId: "
                    + event.getJobId(), ex);
        } catch (Exception ex) {
            log.error("Failed to publish StateUpdateEvent for jobId={}: {}",
                    event.getJobId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to publish StateUpdateEvent for jobId: "
                    + event.getJobId(), ex);
        }
    }
}