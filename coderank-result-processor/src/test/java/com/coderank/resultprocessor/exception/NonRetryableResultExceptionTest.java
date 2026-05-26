package com.coderank.resultprocessor.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NonRetryableResultException — constructor tests")
class NonRetryableResultExceptionTest {

    @Test
    @DisplayName("Message-only constructor preserves message and has null cause")
    void messageOnlyConstructor() {
        NonRetryableResultException ex = new NonRetryableResultException("bad event");

        assertThat(ex.getMessage()).isEqualTo("bad event");
        assertThat(ex.getCause()).isNull();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Message+cause constructor preserves both")
    void messageAndCauseConstructor() {
        Throwable cause = new IllegalArgumentException("root");
        NonRetryableResultException ex = new NonRetryableResultException("wrapped", cause);

        assertThat(ex.getMessage()).isEqualTo("wrapped");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}