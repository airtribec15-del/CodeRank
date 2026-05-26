package com.coderank.execution.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NonRetryableExecutionException}.
 *
 * <p>Verifies that the exception preserves message and cause through both
 * constructors, so RetryableTopic's {@code exclude=} filter can correctly
 * route messages to DLT without burning retry attempts.
 */
@DisplayName("NonRetryableExecutionException")
class NonRetryableExecutionExceptionTest {

    @Test
    @DisplayName("message-only constructor sets message and leaves cause null")
    void messageOnlyConstructor() {
        NonRetryableExecutionException ex =
                new NonRetryableExecutionException("payload is invalid");

        assertThat(ex.getMessage()).isEqualTo("payload is invalid");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("message + cause constructor preserves both")
    void messageAndCauseConstructor() {
        IllegalArgumentException root = new IllegalArgumentException("null jobId");
        NonRetryableExecutionException ex =
                new NonRetryableExecutionException("invalid request", root);

        assertThat(ex.getMessage()).isEqualTo("invalid request");
        assertThat(ex.getCause()).isSameAs(root);
    }

    @Test
    @DisplayName("is a RuntimeException so it propagates without checked-exception handling")
    void isRuntimeException() {
        NonRetryableExecutionException ex = new NonRetryableExecutionException("x");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}