package com.coderank.execution.consumer;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.execution.exception.NonRetryableExecutionException;
import com.coderank.execution.service.CodeExecutionService;
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
 * Consumes {@link CodeExecutionRequestEvent} from {@code code.execution.requests}.
 *
 * <h2>Retry + DLT Strategy</h2>
 * <pre>
 *  Attempt 1 → code.execution.requests          (original topic)
 *  Attempt 2 → code.execution.requests-retry-0  (after 500 ms)
 *  Attempt 3 → code.execution.requests-retry-1  (after 1 s)
 *  Exhausted → code.execution.requests-dlt       (@DltHandler)
 * </pre>
 *
 * <h2>Exception Classification</h2>
 * <ul>
 *   <li><b>Retryable</b>  – Docker daemon temporarily unavailable, thread pool busy → retried.</li>
 *   <li><b>Non-retryable</b> – {@link NonRetryableExecutionException} (unsupported language,
 *       null source code, container image not found) → straight to DLT.</li>
 * </ul>
 *
 * <h2>Offset Acknowledgement</h2>
 * Unlike the submission consumer, offset is acknowledged BEFORE the async
 * Docker run starts.  Rationale: the job is durably tracked in Redis by jobId;
 * if the JVM crashes mid-execution the submission stays QUEUED/RUNNING in Redis
 * and can be reconciled by a periodic cleanup job — no need to re-consume from Kafka.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionRequestConsumer {

    private final CodeExecutionService codeExecutionService;

    // ------------------------------------------------------------------ //
    //  MAIN LISTENER  (code.execution.requests)                          //
    // ------------------------------------------------------------------ //

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5_000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "false",
            exclude = {NonRetryableExecutionException.class}  // ← bad payload: skip retry → DLT
    )
    @KafkaListener(
            topics = KafkaTopics.EXECUTION_REQUESTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload  CodeExecutionRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int    partition,
            @Header(KafkaHeaders.OFFSET)             long   offset,
            Acknowledgment acknowledgment) {

        log.info("[CONSUME] topic={} partition={} offset={} | jobId={} lang={} userId={}",
                topic, partition, offset,
                event.getJobId(), event.getLanguage(), event.getUserId());

        try {
            // 1. Validate payload before acking — catch bad data early
            validateEvent(event);

            // 2. Acknowledge offset: record is durably tracked in Redis by jobId
            acknowledgment.acknowledge();

            // 3. Dispatch to @Async execution pipeline — consumer thread freed immediately
            codeExecutionService.executeAsync(event);

            log.info("[CONSUME] Dispatched | jobId={} lang={}", event.getJobId(), event.getLanguage());

        } catch (NonRetryableExecutionException ex) {
            // Invalid payload — retrying won't fix it → route to DLT immediately
            log.error("[CONSUME] Non-retryable payload error | jobId={}: {}",
                    event.getJobId(), ex.getMessage());
            throw ex;

        } catch (Exception ex) {
            // Transient error (thread pool saturated, Redis unreachable) → let Spring retry
            log.warn("[CONSUME] Transient error | jobId={} (will retry): {}",
                    event.getJobId(), ex.getMessage());
            throw ex;
        }
    }

    // ------------------------------------------------------------------ //
    //  DLT HANDLER  (code.execution.requests-dlt)                       //
    // ------------------------------------------------------------------ //

    /**
     * Invoked automatically by the {@code @RetryableTopic} infrastructure after
     * all retry attempts are exhausted, or immediately for non-retryable exceptions.
     *
     * <p>Must NOT re-throw — that would cause an infinite DLT loop.
     * Instead: log, emit metric, and publish a FAILED result event so the
     * submission does not stay stuck in PENDING state forever.
     */
    @DltHandler
    public void handleDlt(
            @Payload  CodeExecutionRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC)     String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int    partition,
            @Header(KafkaHeaders.OFFSET)             long   offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE)  String exceptionMessage) {

        log.error("[DLT] POISON MESSAGE — topic={} partition={} offset={} | " +
                  "jobId={} lang={} error='{}' — manual intervention required",
                topic, partition, offset,
                event.getJobId(), event.getLanguage(), exceptionMessage);

        // Best-effort: publish a FAILED result so submission is not stuck in PENDING
        try {
            codeExecutionService.publishFailedResult(
                    event.getJobId(),
                    event.getSubmissionId(),
                    "Execution failed after all retries: " + exceptionMessage
            );
        } catch (Exception ex) {
            // Still must NOT re-throw from DLT handler
            log.error("[DLT] Could not publish failed result for jobId={}: {}",
                    event.getJobId(), ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                    //
    // ------------------------------------------------------------------ //

    private void validateEvent(CodeExecutionRequestEvent event) {
        if (event.getJobId() == null) {
            throw new NonRetryableExecutionException("jobId is null");
        }
        if (event.getSubmissionId() == null) {
            throw new NonRetryableExecutionException("submissionId is null");
        }
        if (event.getLanguage() == null) {
            throw new NonRetryableExecutionException("language is null");
        }
        if (event.getSourceCode() == null || event.getSourceCode().isBlank()) {
            throw new NonRetryableExecutionException("sourceCode is blank for jobId=" + event.getJobId());
        }
    }
}
