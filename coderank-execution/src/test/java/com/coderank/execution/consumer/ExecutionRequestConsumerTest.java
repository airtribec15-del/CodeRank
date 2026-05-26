package com.coderank.execution.consumer;

import com.coderank.common.enums.Language;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.execution.service.CodeExecutionService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link ExecutionRequestConsumer}.
 *
 * <p>The production consumer performs NO validation — it dispatches the event to
 * {@link CodeExecutionService#executeAsync(CodeExecutionRequestEvent)} and then
 * acks the offset. Validation of payload contents (null jobId, blank source code,
 * etc.) is the responsibility of upstream services. These tests therefore verify
 * the dispatch/ack contract and the DLT handler's best-effort recovery semantics.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionRequestConsumer")
class ExecutionRequestConsumerTest {

    @Mock
    private CodeExecutionService codeExecutionService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private ExecutionRequestConsumer consumer;

    private CodeExecutionRequestEvent buildEvent(Language language) {
        return CodeExecutionRequestEvent.builder()
                .jobId(UUID.randomUUID())
                .submissionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .problemId(null)
                .language(language)
                .sourceCode("print('hello')")
                .stdinInput("5")
                .timeoutSeconds(10)
                .submittedAt(Instant.now())
                .build();
    }

    // ────────────────────────────────────────────────────────────────────
    //  Main listener: consume()
    // ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("consume()")
    class Consume {

        @Test
        @DisplayName("dispatches to executeAsync THEN acknowledges (Active Object pattern)")
        void shouldDispatchBeforeAck() {
            CodeExecutionRequestEvent event = buildEvent(Language.PYTHON);

            consumer.consume(event, "code.execution.requests", 0, 100L, acknowledgment);

            InOrder order = inOrder(codeExecutionService, acknowledgment);
            order.verify(codeExecutionService).executeAsync(event);
            order.verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("forwards the exact event payload to CodeExecutionService")
        void shouldForwardSameEvent() {
            CodeExecutionRequestEvent event = buildEvent(Language.JAVA);

            consumer.consume(event, "code.execution.requests", 1, 42L, acknowledgment);

            verify(codeExecutionService).executeAsync(event);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("processes Python submission")
        void processesPython() {
            consumer.consume(buildEvent(Language.PYTHON), "t", 0, 1L, acknowledgment);
            verify(codeExecutionService).executeAsync(any(CodeExecutionRequestEvent.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("processes Java submission")
        void processesJava() {
            consumer.consume(buildEvent(Language.JAVA), "t", 0, 2L, acknowledgment);
            verify(codeExecutionService).executeAsync(any(CodeExecutionRequestEvent.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("processes JavaScript submission")
        void processesJavascript() {
            consumer.consume(buildEvent(Language.JAVASCRIPT), "t", 0, 3L, acknowledgment);
            verify(codeExecutionService).executeAsync(any(CodeExecutionRequestEvent.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("processes C++ submission")
        void processesCpp() {
            consumer.consume(buildEvent(Language.CPP), "t", 0, 4L, acknowledgment);
            verify(codeExecutionService).executeAsync(any(CodeExecutionRequestEvent.class));
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("processes SUBMIT-type event (problemId non-null) the same way as RUN-type")
        void processesSubmitTypeEvent() {
            CodeExecutionRequestEvent submit = CodeExecutionRequestEvent.builder()
                    .jobId(UUID.randomUUID())
                    .submissionId(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .problemId(UUID.randomUUID())
                    .language(Language.PYTHON)
                    .sourceCode("print(1)")
                    .timeoutSeconds(10)
                    .submittedAt(Instant.now())
                    .build();

            consumer.consume(submit, "code.execution.requests", 0, 5L, acknowledgment);

            verify(codeExecutionService).executeAsync(submit);
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("propagates exception from executeAsync so retry topic can pick it up (no ack)")
        void shouldNotAckWhenDispatchThrows() {
            CodeExecutionRequestEvent event = buildEvent(Language.PYTHON);
            doThrow(new RuntimeException("executor rejected"))
                    .when(codeExecutionService).executeAsync(event);

            try {
                consumer.consume(event, "code.execution.requests", 0, 1L, acknowledgment);
            } catch (RuntimeException ignored) {
                // expected — RetryableTopic will pick this up
            }

            // ack must NOT happen if dispatch fails — otherwise the message would be lost
            verify(acknowledgment, never()).acknowledge();
        }
    }

    // ────────────────────────────────────────────────────────────────────
    //  DLT handler: handleDlt()
    // ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleDlt()")
    class DltHandler {

        @Test
        @DisplayName("publishes a FAILED result via the 3-arg publishFailedResult overload")
        void shouldPublishFailedResultOnDlt() {
            CodeExecutionRequestEvent event = buildEvent(Language.PYTHON);
            doNothing().when(codeExecutionService)
                    .publishFailedResult(any(UUID.class), any(UUID.class), anyString());

            assertThatNoException().isThrownBy(() ->
                    consumer.handleDlt(event, "code.execution.requests-dlt", 0, 1L,
                            "Execution failed after all retries"));

            verify(codeExecutionService).publishFailedResult(
                    eq(event.getJobId()), eq(event.getSubmissionId()), anyString());
        }

        @Test
        @DisplayName("swallows downstream exception — never rethrows from DLT handler")
        void shouldNotRethrowEvenIfPublishThrows() {
            CodeExecutionRequestEvent event = buildEvent(Language.PYTHON);
            doThrow(new RuntimeException("Kafka unreachable"))
                    .when(codeExecutionService)
                    .publishFailedResult(any(UUID.class), any(UUID.class), anyString());

            assertThatNoException().isThrownBy(() ->
                    consumer.handleDlt(event, "code.execution.requests-dlt", 0, 2L, "err"));
        }

        @Test
        @DisplayName("does NOT call executeAsync or acknowledge — DLT is terminal")
        void shouldNotDispatchOrAckFromDltHandler() {
            CodeExecutionRequestEvent event = buildEvent(Language.PYTHON);
            doNothing().when(codeExecutionService)
                    .publishFailedResult(any(UUID.class), any(UUID.class), anyString());

            consumer.handleDlt(event, "code.execution.requests-dlt", 0, 3L, "err");

            verify(codeExecutionService, never()).executeAsync(any(CodeExecutionRequestEvent.class));
            verifyNoInteractions(acknowledgment);
        }

        @Test
        @DisplayName("includes the exception message in the failure reason forwarded downstream")
        void shouldIncludeExceptionMessageInReason() {
            CodeExecutionRequestEvent event = buildEvent(Language.PYTHON);

            consumer.handleDlt(event, "code.execution.requests-dlt", 0, 4L,
                    "RootCause: NPE at line 42");

            verify(codeExecutionService).publishFailedResult(
                    eq(event.getJobId()),
                    eq(event.getSubmissionId()),
                    org.mockito.ArgumentMatchers.contains("RootCause: NPE at line 42"));
        }
    }
}