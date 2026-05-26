package com.coderank.problem.client;

/**
 * Thrown when Submission Service returns an unexpected 4xx or 5xx
 * during the Problem Service → Submission Service forwarding hop.
 */
public class SubmissionForwardException extends RuntimeException {
    public SubmissionForwardException(String message) {
        super(message);
    }
}