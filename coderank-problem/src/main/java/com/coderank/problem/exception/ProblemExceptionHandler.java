package com.coderank.problem.exception;

import com.coderank.common.dto.response.ErrorResponse;
import com.coderank.common.exception.CodeRankException;
import com.coderank.problem.client.SubmissionForwardException;
import com.coderank.problem.client.SubmissionRateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Global exception handler for Problem Service.
 *
 * <p>Order of resolution (most specific first):
 * <ol>
 *   <li>{@link SubmissionRateLimitException} → 429</li>
 *   <li>{@link SubmissionForwardException}   → 502</li>
 *   <li>{@link CodeRankException}            → status from exception</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 with field errors</li>
 *   <li>{@link Exception} (fallback)         → 500</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class ProblemExceptionHandler {

    // ─────────────────────────────────────────────────────────────
    //  Downstream submission-forwarding errors (Problem → Submission)
    // ─────────────────────────────────────────────────────────────

    @ExceptionHandler(SubmissionRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            SubmissionRateLimitException ex, HttpServletRequest request) {
        log.warn("Submission rate limit: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "RATE_LIMIT_EXCEEDED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(SubmissionForwardException.class)
    public ResponseEntity<ErrorResponse> handleForwardFailure(
            SubmissionForwardException ex, HttpServletRequest request) {
        log.error("Submission forward failed at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(
                        HttpStatus.BAD_GATEWAY.value(),
                        "SUBMISSION_FORWARD_FAILED",
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // ─────────────────────────────────────────────────────────────
    //  Domain exceptions
    // ─────────────────────────────────────────────────────────────

    @ExceptionHandler(CodeRankException.class)
    public ResponseEntity<ErrorResponse> handleCodeRankException(
            CodeRankException ex, HttpServletRequest request) {
        log.warn("CodeRankException at {}: code={} message={}",
                request.getRequestURI(), ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ErrorResponse.of(
                        ex.getHttpStatus().value(),
                        ex.getErrorCode(),
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // ─────────────────────────────────────────────────────────────
    //  Request-validation errors (Jakarta Bean Validation)
    // ─────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        log.warn("Validation failed at {}: {} field error(s)",
                request.getRequestURI(), fieldErrors.size());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("VALIDATION_FAILED")
                .message("Request validation failed")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    // ─────────────────────────────────────────────────────────────
    //  Fallback — anything not caught above
    // ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        request.getRequestURI()
                ));
    }
}