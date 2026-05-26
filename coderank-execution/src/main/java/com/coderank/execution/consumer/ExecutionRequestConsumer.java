package com.coderank.execution.consumer;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.common.exception.InvalidRequestException;
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

import java.util.UUID;

/**
 * Consumes {@link CodeExecutionRequestEvent} from {@code code.execution.requests}.
 *
 * <h2>Retry / DLT Strategy</h2>
 * <pre>
 * Attempt 1: code.execution.requests          (original topic)
 * Attempt 2: code.execution.requests-retry-0  (after 2 s)
 * Attempt 3: code.execution.requests-retry-1  (after 4 s)
 * Attempt 4: code.execution.requests-retry-2  (after 8 s)
 * Exhausted:  code.execution.requests-dlt     (@DltHandler)
 * </pre>
 *
 * <h2>Offset ACK Ordering</h2>
 * The offset is manually ACKed ONLY AFTER the async execution task has been
 * dispatched to the thread pool. The async task itself handles its own result
 * publishing; the consumer's job ends at dispatch.
 *
 * <h2>Active Object Pattern</h2>
 * The consumer dispatches to {@link CodeExecutionService#executeAsync} which
 * runs on a dedicated {@code executionTaskExecutor} thread pool, decoupling
 * the Kafka poll loop from the long-running Docker execution.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionRequestConsumer {

    private final CodeExecutionService codeExecutionService;

    // ── MAIN LISTENER: code.execution.requests ─────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "false",
            exclude = InvalidRequestException.class
    )
    @KafkaListener(
            topics = KafkaTopics.EXECUTION_REQUESTS,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload CodeExecutionRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        // Guard: reject any message missing jobId as a non-retryable poison pill.
        // InvalidRequestException is in @RetryableTopic exclude list, so this
        // routes straight to the DLT without burning the 3 retry attempts.
        // This must happen on the Kafka listener thread (before executeAsync)
        // so the exception propagates through the RetryableTopic machinery.
        // A null jobId inside @Async would be swallowed silently.
        if (event.getJobId() == null) {
            throw new InvalidRequestException(
                    "Rejected unprocessable message: jobId is null (offset=" + offset + ")");
        }

        log.info("CONSUME jobId={} language={} problemId={} topic={} offset={}",
                event.getJobId(), event.getLanguage(), event.getProblemId(), topic, offset);

        // Dispatch to async executor (Active Object pattern) THEN ack.
        // If dispatch itself fails (OOM, executor shut down), we do NOT ack
        // so RetryableTopic can retry.
        codeExecutionService.executeAsync(event);
        acknowledgment.acknowledge();

        log.debug("DISPATCHED & ACKED jobId={}", event.getJobId());
    }

    // ── DLT HANDLER: code.execution.requests-dlt ───────────────────────────

    /**
     * Called after all retry attempts are exhausted or for non-retryable exceptions.
     * Publishes a FAILED result so the submission is never stuck in QUEUED forever.
     * Must NOT re-throw — any exception here would cause the DLT offset to not be
     * committed, causing infinite reprocessing of the DLT message.
     *
     * <h2>Why @Payload is byte[], not CodeExecutionRequestEvent</h2>
     * The DLT is also fed when deserialization itself fails (e.g. a malformed JSON
     * poison pill injected directly into the topic). In that case,
     * ErrorHandlingDeserializer sets the deserialized value to null and stores the
     * exception in a header. If the @Payload type were CodeExecutionRequestEvent,
     * Spring would pass null, and event.getJobId() would throw NPE — crashing the
     * DLT handler and silently dropping the message (failIfNoDestinationReturned=false).
     *
     * Accepting byte[] is always safe: Spring passes raw bytes regardless of whether
     * deserialization succeeded or failed. jobId and submissionId are extracted from
     * Kafka headers instead, which RetryableTopic copies through the retry chain and
     * into the DLT message intact.
     */
    @DltHandler
    public void handleDlt(
            @Payload byte[] rawPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(name = "jobId", required = false) String jobIdHeader,
            @Header(name = "submissionId", required = false) String submissionIdHeader) {

        log.error("DLT POISON REQUEST topic={} partition={} offset={} jobId={} error='{}' — manual intervention required",
                topic, partition, offset, jobIdHeader, exceptionMessage);

        try {
            UUID jobId = (jobIdHeader != null && !jobIdHeader.isBlank())
                    ? UUID.fromString(jobIdHeader) : null;
            UUID submissionId = (submissionIdHeader != null && !submissionIdHeader.isBlank())
                    ? UUID.fromString(submissionIdHeader) : null;

            if (jobId == null) {
                // Completely undeserializable poison pill injected externally —
                // no legitimate submission to mark failed.
                log.error("DLT: Cannot publish failed result — jobId header missing. " +
                                "Raw payload size={} bytes. Likely an externally injected poison pill.",
                        rawPayload != null ? rawPayload.length : 0);
                return;
            }

            codeExecutionService.publishFailedResult(
                    jobId,
                    submissionId,
                    "Execution request permanently failed after all retries: " + exceptionMessage);

        } catch (Exception ex) {
            // Best-effort — must not re-throw from DLT handler.
            log.error("DLT: Could not publish failed result for jobId={}: {}",
                    jobIdHeader, ex.getMessage());
        }
    }
}