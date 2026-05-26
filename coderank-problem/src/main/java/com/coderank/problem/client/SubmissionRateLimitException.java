package com.coderank.problem.client;

/**
 * Thrown when Submission Service returns HTTP 429 (Too Many Requests)
 * during the Problem Service → Submission Service forwarding hop.
 */
public class SubmissionRateLimitException extends RuntimeException {
    public SubmissionRateLimitException(String message) {
        super(message);
    }
}