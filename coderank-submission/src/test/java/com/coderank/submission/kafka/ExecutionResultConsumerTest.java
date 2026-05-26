// src/test/java/com/coderank/submission/kafka/ExecutionResultConsumerTest.java
package com.coderank.submission.kafka;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.submission.service.SubmissionService;
import com.coderank.submission.service.VerdictResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionResultConsumer")
class ExecutionResultConsumerTest {

    @Mock SubmissionService submissionService;
    @Mock VerdictResolutionService verdictResolutionService;
    @Mock Acknowledgment acknowledgment;

    @InjectMocks ExecutionResultConsumer consumer;

    private CodeExecutionResultEvent completedEvent;
    private CodeExecutionResultEvent failedEvent;

    @BeforeEach
    void setUp() {
        UUID jobId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        completedEvent = CodeExecutionResultEvent.builder()
                .jobId(jobId)
                .submissionId(submissionId)
                .status(ExecutionStatus.COMPLETED)
                .stdout("[0,1]")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(120L)
                .completedAt(Instant.now())
                .build();

        failedEvent = CodeExecutionResultEvent.builder()
                .jobId(UUID.randomUUID())
                .submissionId(UUID.randomUUID())
                .status(ExecutionStatus.FAILED)
                .stdout("")
                .stderr("runtime crash")
                .exitCode(1)
                .executionTimeMs(50L)
                .completedAt(Instant.now())
                .build();
    }

    // ------------------------------------------------------------------ //
    //  consume() — happy path                                            //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("consume — happy path")
    class ConsumeHappyPath {

        @Test
        @DisplayName("resolves verdict, updates submission, then acknowledges offset (in order)")
        void shouldResolveAndAcknowledge() {
            when(verdictResolutionService.resolve(completedEvent)).thenReturn(Verdict.ACCEPTED);

            consumer.consume(completedEvent, "code.execution.results", 0, 0L, acknowledgment);

            InOrder inOrder = inOrder(verdictResolutionService, submissionService, acknowledgment);
            inOrder.verify(verdictResolutionService).resolve(completedEvent);
            inOrder.verify(submissionService).updateSubmissionResult(
                    eq(completedEvent.getJobId()),
                    eq(ExecutionStatus.COMPLETED),
                    eq("[0,1]"),
                    eq(""),
                    eq(0),
                    eq(120L),
                    eq(Verdict.ACCEPTED)
            );
            inOrder.verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("resolves RUNTIME_ERROR verdict for failed event and acknowledges")
        void shouldHandleFailedEventAndAcknowledge() {
            when(verdictResolutionService.resolve(failedEvent)).thenReturn(Verdict.RUNTIME_ERROR);

            consumer.consume(failedEvent, "code.execution.results", 0, 1L, acknowledgment);

            verify(submissionService).updateSubmissionResult(
                    eq(failedEvent.getJobId()),
                    eq(ExecutionStatus.FAILED),
                    eq(""),
                    eq("runtime crash"),
                    eq(1),
                    eq(50L),
                    eq(Verdict.RUNTIME_ERROR));
            verify(acknowledgment).acknowledge();
        }
    }

    // ------------------------------------------------------------------ //
    //  consume() — non-retryable (InvalidRequestException)               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("consume — non-retryable error")
    class ConsumeNonRetryable {

        @Test
        @DisplayName("re-throws InvalidRequestException without acknowledging (routes to DLT immediately)")
        void shouldRethrowInvalidRequestException() {
            when(verdictResolutionService.resolve(completedEvent)).thenReturn(Verdict.ACCEPTED);
            doThrow(new InvalidRequestException("No submission for jobId"))
                    .when(submissionService).updateSubmissionResult(
                            any(), any(), any(), any(), any(), any(), any());

            assertThatThrownBy(() ->
                    consumer.consume(completedEvent, "code.execution.results", 0, 0L, acknowledgment))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("No submission for jobId");

            verify(acknowledgment, never()).acknowledge();
        }
    }

    // ------------------------------------------------------------------ //
    //  consume() — retryable (transient exception)                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("consume — transient/retryable error")
    class ConsumeRetryable {

        @Test
        @DisplayName("re-throws transient RuntimeException without acknowledging (triggers retry topic)")
        void shouldRethrowTransientExceptionWithoutAck() {
            when(verdictResolutionService.resolve(completedEvent)).thenReturn(Verdict.ACCEPTED);
            doThrow(new RuntimeException("DB connection lost"))
                    .when(submissionService).updateSubmissionResult(
                            any(), any(), any(), any(), any(), any(), any());

            assertThatThrownBy(() ->
                    consumer.consume(completedEvent, "code.execution.results", 0, 0L, acknowledgment))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB connection lost");

            verify(acknowledgment, never()).acknowledge();
        }

        @Test
        @DisplayName("re-throws exception from verdict resolver without acknowledging")
        void shouldRethrowVerdictResolverException() {
            when(verdictResolutionService.resolve(completedEvent))
                    .thenThrow(new RuntimeException("verdict resolver crash"));

            assertThatThrownBy(() ->
                    consumer.consume(completedEvent, "code.execution.results", 0, 0L, acknowledgment))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("verdict resolver crash");

            verify(submissionService, never()).updateSubmissionResult(
                    any(), any(), any(), any(), any(), any(), any());
            verify(acknowledgment, never()).acknowledge();
        }
    }

    // ------------------------------------------------------------------ //
    //  handleDlt()                                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("handleDlt")
    class HandleDlt {

        @Test
        @DisplayName("marks submission as DLT-failed and never re-throws")
        void shouldMarkAsDltFailedAndNotThrow() {
            assertThatNoException().isThrownBy(() ->
                    consumer.handleDlt(
                            completedEvent,
                            "code.execution.results-dlt",
                            0, 0L,
                            "Retries exhausted"));

            verify(submissionService).markSubmissionAsDltFailed(completedEvent.getJobId());
        }

        @Test
        @DisplayName("does NOT re-throw even if markSubmissionAsDltFailed itself throws")
        void shouldSwallowExceptionFromMarkAsDltFailed() {
            doThrow(new RuntimeException("DB also down"))
                    .when(submissionService).markSubmissionAsDltFailed(any());

            assertThatNoException().isThrownBy(() ->
                    consumer.handleDlt(
                            completedEvent,
                            "code.execution.results-dlt",
                            0, 0L,
                            "original error"));

            verify(submissionService).markSubmissionAsDltFailed(completedEvent.getJobId());
        }

        @Test
        @DisplayName("calls markSubmissionAsDltFailed with correct jobId from event")
        void shouldPassCorrectJobId() {
            consumer.handleDlt(
                    failedEvent,
                    "code.execution.results-dlt",
                    0, 5L,
                    "some error");

            verify(submissionService).markSubmissionAsDltFailed(failedEvent.getJobId());
        }
    }
}