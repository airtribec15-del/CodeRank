package com.coderank.execution.consumer;

import com.coderank.common.enums.Language;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.execution.exception.NonRetryableExecutionException;
import com.coderank.execution.service.CodeExecutionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionRequestConsumer")
class ExecutionRequestConsumerTest {

    @Mock private CodeExecutionService codeExecutionService;
    @Mock private Acknowledgment acknowledgment;
    @InjectMocks private ExecutionRequestConsumer consumer;

    private CodeExecutionRequestEvent validEvent(Language lang) {
        return CodeExecutionRequestEvent.builder()
                .jobId(UUID.randomUUID()).submissionId(UUID.randomUUID())
                .userId(UUID.randomUUID()).language(lang)
                .sourceCode("print('hello')").stdinInput("5")
                .timeoutSeconds(10).submittedAt(Instant.now())
                .build();
    }

    // ── Happy path ──────────────────────────────────────────────────────

    @Test @DisplayName("acks offset BEFORE dispatching async execution")
    void shouldAckBeforeDispatch() {
        var ev = validEvent(Language.PYTHON);
        consumer.consume(ev, "code.execution.requests", 0, 1L, acknowledgment);

        InOrder order = inOrder(acknowledgment, codeExecutionService);
        order.verify(acknowledgment).acknowledge();
        order.verify(codeExecutionService).executeAsync(ev);
    }

    @Test @DisplayName("dispatches to CodeExecutionService.executeAsync")
    void shouldDispatchToService() {
        var ev = validEvent(Language.JAVA);
        consumer.consume(ev, "code.execution.requests", 0, 2L, acknowledgment);
        verify(codeExecutionService).executeAsync(ev);
    }

    @Test @DisplayName("processes Python submission")
    void python() { consumer.consume(validEvent(Language.PYTHON), "t", 0, 1, acknowledgment); verify(codeExecutionService).executeAsync(any()); }

    @Test @DisplayName("processes JavaScript submission")
    void javascript() { consumer.consume(validEvent(Language.JAVASCRIPT), "t", 0, 2, acknowledgment); verify(codeExecutionService).executeAsync(any()); }

    @Test @DisplayName("processes C++ submission")
    void cpp() { consumer.consume(validEvent(Language.CPP), "t", 0, 3, acknowledgment); verify(codeExecutionService).executeAsync(any()); }

    // ── Validation — non-retryable path ────────────────────────────────

    @Test @DisplayName("null jobId → NonRetryableExecutionException, no ack, no dispatch")
    void nullJobIdIsNonRetryable() {
        var ev = CodeExecutionRequestEvent.builder()
                .jobId(null).submissionId(UUID.randomUUID())
                .language(Language.PYTHON).sourceCode("print(1)")
                .timeoutSeconds(10).submittedAt(Instant.now()).build();

        assertThatThrownBy(() -> consumer.consume(ev, "t", 0, 1L, acknowledgment))
                .isInstanceOf(NonRetryableExecutionException.class);

        verify(acknowledgment, never()).acknowledge();
        verifyNoInteractions(codeExecutionService);
    }

    @Test @DisplayName("null language → NonRetryableExecutionException")
    void nullLanguageIsNonRetryable() {
        var ev = CodeExecutionRequestEvent.builder()
                .jobId(UUID.randomUUID()).submissionId(UUID.randomUUID())
                .language(null).sourceCode("print(1)")
                .timeoutSeconds(10).submittedAt(Instant.now()).build();

        assertThatThrownBy(() -> consumer.consume(ev, "t", 0, 1L, acknowledgment))
                .isInstanceOf(NonRetryableExecutionException.class);
    }

    @Test @DisplayName("blank sourceCode → NonRetryableExecutionException")
    void blankSourceCodeIsNonRetryable() {
        var ev = CodeExecutionRequestEvent.builder()
                .jobId(UUID.randomUUID()).submissionId(UUID.randomUUID())
                .language(Language.JAVA).sourceCode("   ")
                .timeoutSeconds(10).submittedAt(Instant.now()).build();

        assertThatThrownBy(() -> consumer.consume(ev, "t", 0, 1L, acknowledgment))
                .isInstanceOf(NonRetryableExecutionException.class)
                .hasMessageContaining("sourceCode is blank");
    }

    @Test @DisplayName("null submissionId → NonRetryableExecutionException")
    void nullSubmissionIdIsNonRetryable() {
        var ev = CodeExecutionRequestEvent.builder()
                .jobId(UUID.randomUUID()).submissionId(null)
                .language(Language.PYTHON).sourceCode("print(1)")
                .timeoutSeconds(10).submittedAt(Instant.now()).build();

        assertThatThrownBy(() -> consumer.consume(ev, "t", 0, 1L, acknowledgment))
                .isInstanceOf(NonRetryableExecutionException.class);
    }

    // ── @DltHandler ────────────────────────────────────────────────────

    @Test @DisplayName("@DltHandler calls publishFailedResult as best-effort")
    void dltHandlerCallsPublishFailedResult() {
        var ev = validEvent(Language.PYTHON);
        doNothing().when(codeExecutionService).publishFailedResult(any(), any(), any());

        assertThatNoException().isThrownBy(() ->
                consumer.handleDlt(ev, "code.execution.requests-dlt", 0, 1L,
                        "Execution failed after retries"));

        verify(codeExecutionService).publishFailedResult(
                eq(ev.getJobId()), eq(ev.getSubmissionId()), anyString());
    }

    @Test @DisplayName("@DltHandler does NOT rethrow even if publishFailedResult throws")
    void dltHandlerDoesNotRethrowOnPublishFailure() {
        var ev = validEvent(Language.PYTHON);
        doThrow(new RuntimeException("Kafka down"))
                .when(codeExecutionService).publishFailedResult(any(), any(), any());

        // Must NOT rethrow — infinite DLT loop would result
        assertThatNoException().isThrownBy(() ->
                consumer.handleDlt(ev, "code.execution.requests-dlt", 0, 2L, "err"));
    }

    @Test @DisplayName("@DltHandler never calls executeAsync")
    void dltHandlerDoesNotDispatchExecution() {
        var ev = validEvent(Language.PYTHON);
        doNothing().when(codeExecutionService).publishFailedResult(any(), any(), any());

        consumer.handleDlt(ev, "code.execution.requests-dlt", 0, 3L, "err");

        verify(codeExecutionService, never()).executeAsync(any());
        verify(acknowledgment, never()).acknowledge();
    }
}
