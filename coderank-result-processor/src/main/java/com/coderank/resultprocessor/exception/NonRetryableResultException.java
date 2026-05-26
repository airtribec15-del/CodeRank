package com.coderank.resultprocessor.exception;

/**
 * Thrown when a {@code CodeExecutionResultEvent} is malformed in a way that
 * retrying will never succeed (e.g., null submissionId).
 *
 * <p>Excluded from {@code @RetryableTopic} retry attempts — the message routes
 * directly to the DLT without consuming retry budget.
 */
public class NonRetryableResultException extends RuntimeException {

    public NonRetryableResultException(String message) {
        super(message);
    }

    public NonRetryableResultException(String message, Throwable cause) {
        super(message, cause);
    }
}