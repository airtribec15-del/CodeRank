package com.coderank.resultprocessor.exception;

import com.coderank.common.dto.response.ErrorResponse;
import com.coderank.common.exception.CodeRankException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for any REST endpoints exposed by this service
 * (currently only actuator and swagger — included for future-proofing).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CodeRankException.class)
    public ResponseEntity<ErrorResponse> handleCodeRankException(
            CodeRankException ex, HttpServletRequest request) {
        log.warn("CodeRankException at {}: {}", request.getRequestURI(), ex.getMessage());
        // FIX: CodeRankException exposes getHttpStatus(), NOT getStatus()
        ErrorResponse body = ErrorResponse.of(
                ex.getHttpStatus().value(),
                ex.getHttpStatus().getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse body = ErrorResponse.of(
                500,
                "Internal Server Error",
                "An unexpected error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.internalServerError().body(body);
    }
}