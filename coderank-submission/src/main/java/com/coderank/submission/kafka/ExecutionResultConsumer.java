package com.coderank.submission.kafka;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.submission.enums.Verdict;
import com.coderank.submission.service.SubmissionService;
import com.coderank.submission.service.VerdictResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link CodeExecutionResultEvent} from {@code code.execution.results}.
 *
 * <h2>Retry + DLT Strategy</h2>
 * <pre>
 *  Attempt 1 → code.execution.results          (original topic)
 *  Attempt 2 → code.execution.results-retry-0  (after 1 s)
 *  Attempt 3 → code.execution.results-retry-1  (after 2 s)
 *  Exhausted → code.execution.results-dlt       (@DltHandler)
 * </pre>
 *
 * <h2>Exception Classification</h2>
 * <ul>
 *   <li><b>Retryable</b>  – transient DB/Redis errors → Spring retries via retry topics.</li>
 *   <li><b>Non-retryable</b> – {@link InvalidRequestException} (bad data) → goes straight
 *       to DLT without wasting retries.</li>
 * </ul>
 *
 * <h2>Offset Acknowledgement</h2>
 * Offset is committed only after a successful DB write so the record is never
 * silently lost on a crash between consume and persist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionResultConsumer {

    private final SubmissionService submissionService;
    private final VerdictResolutionService verdictResolutionService;

    // ------------------------------------------------------------------ //
    //  MAIN LISTENER  (code.execution.results)                           //
    // ------------------------------------------------------------------ //

    /**
     * @RetryableTopic wires up:
     *  - attempts = 3  → 1 original + 2 retry topics
     *  - exponential backoff 1 s → 2 s
     *  - non-retryable: InvalidRequestException (bad jobId / missing row) – skip retries
     *  - autoCreateTopics = false  → topics are created explicitly in KafkaConfig
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1_000, multiplier = 2.0, maxDelay = 10_000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "false",
            exclude = {InvalidRequestException.class}   // ← non-retryable: goes straight to DLT
    )
    @KafkaListener(
            topics = KafkaTopics.EXECUTION_RESULTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload  CodeExecutionResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int    partition,
            @Header(KafkaHeaders.OFFSET)             long   offset,
            Acknowledgment acknowledgment) {

        log.info("[CONSUME] topic={} partition={} offset={} | jobId={} status={}",
                topic, partition, offset, event.getJobId(), event.getStatus());

        try {
            // 1. Resolve verdict from raw execution result
            Verdict verdict = verdictResolutionService.resolve(event);

            // 2. Persist to DB (transactional – throws InvalidRequestException if jobId unknown)
            submissionService.updateSubmissionResult(
                    event.getJobId(),
                    event.getStatus(),
                    event.getStdout(),
                    event.getStderr(),
                    event.getExitCode(),
                    event.getExecutionTimeMs(),
                    verdict
            );

            // 3. Commit offset ONLY after successful DB write
            acknowledgment.acknowledge();

            log.info("[CONSUME] OK | jobId={} verdict={}", event.getJobId(), verdict);

        } catch (InvalidRequestException ex) {
            // Bad data (unknown jobId, schema mismatch) – retrying won't help, route to DLT
            log.error("[CONSUME] Non-retryable error for jobId={}: {}", event.getJobId(), ex.getMessage());
            throw ex;   // @RetryableTopic sees this in `exclude` and skips retry → DLT

        } catch (Exception ex) {
            // Transient error (DB down, Redis timeout, network blip) – let @RetryableTopic retry
            log.warn("[CONSUME] Transient error for jobId={} (will retry): {}",
                    event.getJobId(), ex.getMessage());
            throw ex;   // Re-throw so Spring does NOT ack and routes to retry topic
        }
    }

    // ------------------------------------------------------------------ //
    //  DLT HANDLER  (code.execution.results-dlt)                        //
    // ------------------------------------------------------------------ //

    /**
     * Called by Spring Kafka AUTOMATICALLY after all retry attempts are exhausted,
     * OR immediately when a non-retryable exception is thrown.
     *
     * <p>This IS the {@code consumeDlt} equivalent — {@code @DltHandler} is the
     * proper Spring Kafka API for handling DLT within a {@code @RetryableTopic} class.
     * A plain {@code @KafkaListener(topics = "...dlt")} would NOT be invoked by the
     * RetryableTopic infrastructure; only {@code @DltHandler} is.
     *
     * <p>Contract: must NOT re-throw — doing so would cause an infinite DLT loop.
     */
    @DltHandler
    public void handleDlt(
            @Payload  CodeExecutionResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int    partition,
            @Header(KafkaHeaders.OFFSET)             long   offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE)  String exceptionMessage) {

        // Safe: log all available context for manual triage
        log.error("[DLT] POISON MESSAGE — topic={} partition={} offset={} | " +
                  "jobId={} status={} error='{}' — manual intervention required",
                topic, partition, offset,
                event.getJobId(), event.getStatus(), exceptionMessage);

        // TODO production hardening:
        //  - io.micrometer.core.instrument.MeterRegistry → increment "kafka.dlt.messages" counter
        //  - Slack / PagerDuty webhook for CRITICAL verdict failures
        //  - Persist a FAILED_DLT sentinel row so the user sees an error state, not PENDING forever
        try {
            submissionService.markSubmissionAsDltFailed(event.getJobId());
        } catch (Exception ex) {
            // Best-effort: if this also fails, we still must not re-throw
            log.error("[DLT] Could not mark submission as DLT_FAILED for jobId={}: {}",
                    event.getJobId(), ex.getMessage());
        }
    }
}
