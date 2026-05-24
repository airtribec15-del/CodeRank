package com.coderank.execution.exception;

/**
 * Thrown when an execution request has an unrecoverable payload problem
 * (null jobId, blank source code, unsupported language, etc.).
 *
 * Marked as non-retryable in {@code @RetryableTopic(exclude = ...)}
 * so the message is routed straight to the DLT without wasting retry attempts.
 */
public class NonRetryableExecutionException extends RuntimeException {

    public NonRetryableExecutionException(String message) {
        super(message);
    }

    public NonRetryableExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
