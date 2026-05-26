package com.coderank.resultprocessor.consumer;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.resultprocessor.exception.NonRetryableResultException;
import com.coderank.resultprocessor.service.ResultProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionResultConsumer — Kafka listener & DLT handler")
class ExecutionResultConsumerTest {

    @Mock
    private ResultProcessorService resultProcessorService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private ExecutionResultConsumer consumer;

    private CodeExecutionResultEvent event;

    @BeforeEach
    void setUp() {
        event = CodeExecutionResultEvent.builder()
                .jobId(UUID.randomUUID())
                .submissionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .problemId(UUID.randomUUID())
                .status(ExecutionStatus.COMPLETED)
                .verdict(Verdict.ACCEPTED)
                .stdout("ok")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(100L)
                .completedAt(Instant.now())
                .build();
    }

    /* ---------- consume() ---------- */

    @Test
    @DisplayName("Happy path: process called, then acknowledge in correct order")
    void happyPathAcksAfterProcessing() {
        consumer.consume(event, "code.execution.results", 0, 42L, acknowledgment);

        InOrder inOrder = inOrder(resultProcessorService, acknowledgment);
        inOrder.verify(resultProcessorService).process(event);
        inOrder.verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("RuntimeException from service propagates and ACK is NOT called")
    void runtimeExceptionPreventsAck() {
        doThrow(new RuntimeException("transient")).when(resultProcessorService).process(any());

        assertThatThrownBy(() ->
                consumer.consume(event, "code.execution.results", 0, 42L, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("transient");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("NonRetryableResultException propagates so RetryableTopic routes directly to DLT")
    void nonRetryableExceptionPropagates() {
        doThrow(new NonRetryableResultException("null submissionId"))
                .when(resultProcessorService).process(any());

        assertThatThrownBy(() ->
                consumer.consume(event, "code.execution.results", 0, 42L, acknowledgment))
                .isInstanceOf(NonRetryableResultException.class)
                .hasMessageContaining("null submissionId");

        verify(acknowledgment, never()).acknowledge();
    }

    /* ---------- handleDlt() ---------- */

    @Test
    @DisplayName("DLT handler logs and never throws on a normal payload")
    void dltHandlerHandlesNormalPayload() {
        assertThatCode(() -> consumer.handleDlt(
                event, "code.execution.results-dlt", 0, 99L, "Max retries exhausted"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DLT handler tolerates null event payload without throwing")
    void dltHandlerTolerratesNullPayload() {
        assertThatCode(() -> consumer.handleDlt(
                null, "code.execution.results-dlt", 0, 99L, "Deserialization failed"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DLT handler tolerates null exception message")
    void dltHandlerTolerratesNullExceptionMessage() {
        assertThatCode(() -> consumer.handleDlt(
                event, "code.execution.results-dlt", 0, 99L, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DLT handler must NOT call the processor service")
    void dltHandlerDoesNotInvokeProcessor() {
        consumer.handleDlt(event, "code.execution.results-dlt", 0, 99L, "boom");
        verifyNoInteractions(resultProcessorService);
    }
}