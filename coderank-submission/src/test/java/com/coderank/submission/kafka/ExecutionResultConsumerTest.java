package com.coderank.submission.kafka;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.submission.enums.Verdict;
import com.coderank.submission.service.SubmissionService;
import com.coderank.submission.service.VerdictResolutionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionResultConsumer")
class ExecutionResultConsumerTest {

    @Mock private SubmissionService submissionService;
    @Mock private VerdictResolutionService verdictResolutionService;
    @Mock private Acknowledgment acknowledgment;
    @InjectMocks private ExecutionResultConsumer consumer;

    private CodeExecutionResultEvent event(ExecutionStatus status, String stdout, String stderr, Integer exit) {
        return CodeExecutionResultEvent.builder()
                .jobId(UUID.randomUUID()).submissionId(UUID.randomUUID())
                .status(status).stdout(stdout).stderr(stderr)
                .exitCode(exit).executionTimeMs(100L).completedAt(Instant.now())
                .build();
    }

    // ── Happy path ──────────────────────────────────────────────────────

    @Test @DisplayName("resolves verdict, persists, then acknowledges offset")
    void shouldResolveAndAck() {
        var ev = event(ExecutionStatus.COMPLETED, "[0,1]", "", 0);
        when(verdictResolutionService.resolve(ev)).thenReturn(Verdict.ACCEPTED);
        doNothing().when(submissionService).updateSubmissionResult(any(), any(), any(), any(), any(), any(), any());

        consumer.consume(ev, "code.execution.results", 0, 1L, acknowledgment);

        InOrder order = inOrder(submissionService, acknowledgment);
        order.verify(submissionService).updateSubmissionResult(eq(ev.getJobId()),
                eq(ExecutionStatus.COMPLETED), eq("[0,1]"), eq(""), eq(0), eq(100L), eq(Verdict.ACCEPTED));
        order.verify(acknowledgment).acknowledge();
    }

    @Test @DisplayName("TIMED_OUT → TIME_LIMIT_EXCEEDED verdict saved")
    void shouldHandleTimedOut() {
        var ev = event(ExecutionStatus.TIMED_OUT, "", "", 1);
        when(verdictResolutionService.resolve(ev)).thenReturn(Verdict.TIME_LIMIT_EXCEEDED);

        consumer.consume(ev, "code.execution.results", 0, 2L, acknowledgment);

        verify(submissionService).updateSubmissionResult(any(), eq(ExecutionStatus.TIMED_OUT),
                any(), any(), any(), any(), eq(Verdict.TIME_LIMIT_EXCEEDED));
        verify(acknowledgment).acknowledge();
    }

    // ── Non-retryable path ─────────────────────────────────────────────

    @Test @DisplayName("InvalidRequestException → rethrown (non-retryable, no ack)")
    void shouldRethrowNonRetryableAndNotAck() {
        var ev = event(ExecutionStatus.COMPLETED, "ok", "", 0);
        when(verdictResolutionService.resolve(ev)).thenReturn(Verdict.ACCEPTED);
        doThrow(new InvalidRequestException("No submission found for jobId: " + ev.getJobId()))
                .when(submissionService).updateSubmissionResult(any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() ->
                consumer.consume(ev, "code.execution.results", 0, 5L, acknowledgment))
                .isInstanceOf(InvalidRequestException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    // ── Retryable path ─────────────────────────────────────────────────

    @Test @DisplayName("transient RuntimeException → rethrown (triggers retry), no ack")
    void shouldRethrowTransientAndNotAck() {
        var ev = event(ExecutionStatus.COMPLETED, "ok", "", 0);
        when(verdictResolutionService.resolve(ev)).thenReturn(Verdict.ACCEPTED);
        doThrow(new RuntimeException("DB connection lost"))
                .when(submissionService).updateSubmissionResult(any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() ->
                consumer.consume(ev, "code.execution.results", 0, 6L, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB connection lost");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test @DisplayName("VerdictResolutionService throws → rethrown, no ack")
    void shouldRethrowWhenVerdictResolutionFails() {
        var ev = event(ExecutionStatus.COMPLETED, "ok", "", 0);
        when(verdictResolutionService.resolve(ev)).thenThrow(new RuntimeException("Verdict error"));

        assertThatThrownBy(() ->
                consumer.consume(ev, "code.execution.results", 0, 7L, acknowledgment))
                .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
        verifyNoInteractions(submissionService);
    }

    // ── @DltHandler ────────────────────────────────────────────────────

    @Test @DisplayName("@DltHandler calls markSubmissionAsDltFailed (best-effort)")
    void dltHandlerCallsMarkAsDltFailed() {
        var ev = event(ExecutionStatus.FAILED, null, "fatal", 1);
        doNothing().when(submissionService).markSubmissionAsDltFailed(ev.getJobId());

        assertThatNoException().isThrownBy(() ->
                consumer.handleDlt(ev, "code.execution.results-dlt", 0, 1L, "DB down after 3 retries"));

        verify(submissionService).markSubmissionAsDltFailed(ev.getJobId());
    }

    @Test @DisplayName("@DltHandler does NOT rethrow even if markAsDltFailed itself throws")
    void dltHandlerDoesNotRethrowOnMarkFailure() {
        var ev = event(ExecutionStatus.FAILED, null, "fatal", 1);
        doThrow(new RuntimeException("Redis down"))
                .when(submissionService).markSubmissionAsDltFailed(any());

        // Must NOT rethrow — doing so would cause infinite DLT loop
        assertThatNoException().isThrownBy(() ->
                consumer.handleDlt(ev, "code.execution.results-dlt", 0, 2L, "original error"));
    }

    @Test @DisplayName("@DltHandler never calls updateSubmissionResult")
    void dltHandlerDoesNotCallUpdateResult() {
        var ev = event(ExecutionStatus.FAILED, null, "fatal", 1);
        doNothing().when(submissionService).markSubmissionAsDltFailed(any());

        consumer.handleDlt(ev, "code.execution.results-dlt", 0, 3L, "error");

        verify(submissionService, never()).updateSubmissionResult(any(), any(), any(), any(), any(), any(), any());
        verify(acknowledgment, never()).acknowledge();
    }
}
