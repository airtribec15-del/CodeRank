package com.coderank.resultprocessor.consumer;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.resultprocessor.exception.NonRetryableResultException;
import com.coderank.resultprocessor.service.ResultProcessorService;
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
 * Attempt 1 → code.execution.results (original topic)
 * Attempt 2 → code.execution.results-retry-0 (after 2s)
 * Attempt 3 → code.execution.results-retry-1 (after 4s)
 * Attempt 4 → code.execution.results-retry-2 (after 8s)
 * Exhausted → code.execution.results-dlt
 * </pre>
 *
 * <h2>ACK Ordering</h2>
 * Offset is ACKed ONLY AFTER {@link ResultProcessorService#process(CodeExecutionResultEvent)}
 * completes successfully (all 4 pipeline steps). If any step throws, the offset
 * is NOT ACKed, allowing @RetryableTopic to deliver to the retry topic.
 *
 * <p>This is different from the Execution Service consumer, which ACKs before
 * async dispatch. Here, the pipeline is synchronous and fast (< 500ms expected),
 * so we can safely block the consumer thread through all steps before ACKing.
 *
 * <h2>Non-Retryable Events</h2>
 * Events that throw {@link NonRetryableResultException} (e.g., null submissionId,
 * 4xx from Submission Service) are excluded from retries and go directly to DLT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionResultConsumer {

    private final ResultProcessorService resultProcessorService;

    // ─── MAIN LISTENER ────────────────────────────────────────────────────────

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            autoCreateTopics = "false",
            exclude = NonRetryableResultException.class
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

        log.info("CONSUME jobId={} submissionId={} status={} verdict={} | topic={} offset={}",
                event.getJobId(), event.getSubmissionId(),
                event.getStatus(), event.getVerdict(),
                topic, offset);

        // Process all 4 pipeline steps synchronously.
        // Any exception prevents the ACK below — Kafka will redeliver to retry topic.
        resultProcessorService.process(event);

        // ACK only after full pipeline success.
        acknowledgment.acknowledge();
        log.debug("ACKED jobId={} offset={}", event.getJobId(), offset);
    }

    // ─── DLT HANDLER ─────────────────────────────────────────────────────────

    /**
     * Called after all retry attempts are exhausted, or immediately for
     * {@link NonRetryableResultException}. Logs the failure for manual
     * intervention. Must NOT re-throw.
     *
     * <p>At this point, the submission is permanently stuck in a non-terminal
     * state in the DB. An alerting system should monitor this DLT topic and
     * trigger manual remediation or a compensating transaction.
     */
    @DltHandler
    public void handleDlt(
            @Payload CodeExecutionResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        log.error(
                "DLT POISON RESULT | topic={} partition={} offset={} | " +
                        "jobId={} submissionId={} error='{}' | MANUAL INTERVENTION REQUIRED",
                topic, partition, offset,
                event != null ? event.getJobId() : "null",
                event != null ? event.getSubmissionId() : "null",
                exceptionMessage
        );

        // Future enhancement: publish to a dead-letter-audit table via direct DB write
        // or trigger a PagerDuty/alerting webhook here.
    }
}