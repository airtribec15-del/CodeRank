package com.coderank.submission.kafka;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.common.exception.InvalidRequestException;
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
 * <h2>Retry / DLT Strategy</h2>
 * <pre>
 * Attempt 1: code.execution.results          (original topic)
 * Attempt 2: code.execution.results-retry-0  (after 1 s)
 * Attempt 3: code.execution.results-retry-1  (after 2 s)
 * Exhausted:  code.execution.results-dlt     (@DltHandler)
 * </pre>
 *
 * <h2>Exception Classification</h2>
 * <ul>
 *   <li><b>Retryable</b>: transient DB/Redis errors — Spring retries via retry topics.</li>
 *   <li><b>Non-retryable</b>: {@link InvalidRequestException} bad data → DLT immediately.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionResultConsumer {

    private final SubmissionService submissionService;
    private final VerdictResolutionService verdictResolutionService;

    // ── MAIN LISTENER: code.execution.results ──────────────────────────────

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "false",
            exclude = InvalidRequestException.class
    )
    @KafkaListener(
            topics = KafkaTopics.EXECUTION_RESULTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload CodeExecutionResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("CONSUME topic={} partition={} offset={} jobId={} status={}",
                topic, partition, offset, event.getJobId(), event.getStatus());
        try {
            // 1. Resolve verdict from raw execution result
            Verdict verdict = verdictResolutionService.resolve(event);

            // 2. Persist to DB — transactional; throws InvalidRequestException if jobId unknown
            submissionService.updateSubmissionResult(
                    event.getJobId(), event.getStatus(),
                    event.getStdout(), event.getStderr(),
                    event.getExitCode(), event.getExecutionTimeMs(),
                    verdict);

            // 3. Commit offset ONLY after successful DB write
            acknowledgment.acknowledge();
            log.info("CONSUME OK jobId={} verdict={}", event.getJobId(), verdict);

        } catch (InvalidRequestException ex) {
            // Bad data — unknown jobId, schema mismatch. Retrying won't help → DLT immediately.
            log.error("CONSUME Non-retryable error for jobId={}: {}", event.getJobId(), ex.getMessage());
            throw ex; // @RetryableTopic sees exclude = InvalidRequestException.class → straight to DLT

        } catch (Exception ex) {
            // Transient error — DB down, Redis timeout, network blip → let RetryableTopic retry
            log.warn("CONSUME Transient error for jobId={}, will retry: {}", event.getJobId(), ex.getMessage());
            throw ex; // Re-throw so Spring does NOT ack and routes to retry topic
        }
    }

    // ── DLT HANDLER: code.execution.results-dlt ────────────────────────────

    /**
     * Called by Spring Kafka automatically after all retry attempts are exhausted,
     * OR immediately when a non-retryable exception is thrown.
     *
     * <p>Contract: must NOT re-throw — doing so would cause an infinite DLT loop.</p>
     */
    @DltHandler
    public void handleDlt(
            @Payload CodeExecutionResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error("DLT POISON MESSAGE topic={} partition={} offset={} jobId={} status={} error='{}' — manual intervention required",
                topic, partition, offset, event.getJobId(), event.getStatus(), exceptionMessage);

        try {
            submissionService.markSubmissionAsDltFailed(event.getJobId());
        } catch (Exception ex) {
            // Best-effort — if this also fails, we still must not re-throw
            log.error("DLT: Could not mark submission as DLT-FAILED for jobId={}: {}",
                    event.getJobId(), ex.getMessage());
        }
    }
}