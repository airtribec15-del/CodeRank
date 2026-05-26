package com.coderank.resultprocessor.exception;

import com.coderank.common.dto.response.ErrorResponse;
import com.coderank.common.exception.CodeRankException;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler — error mapping tests")
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("CodeRankException → maps to its httpStatus and message")
    void handlesCodeRankException() {
        when(request.getRequestURI()).thenReturn("/actuator/foo");
        CodeRankException ex = new CodeRankException(
                "boom", HttpStatus.UNPROCESSABLE_ENTITY, "CUSTOM_CODE");

        ResponseEntity<ErrorResponse> response = handler.handleCodeRankException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(422);
        assertThat(body.getError()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase());
        assertThat(body.getMessage()).isEqualTo("boom");
        assertThat(body.getPath()).isEqualTo("/actuator/foo");
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("InvalidRequestException (subclass) → 400 BAD_REQUEST")
    void handlesInvalidRequestException() {
        when(request.getRequestURI()).thenReturn("/v3/api-docs");
        InvalidRequestException ex = new InvalidRequestException("bad input");

        ResponseEntity<ErrorResponse> response = handler.handleCodeRankException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
        assertThat(response.getBody().getPath()).isEqualTo("/v3/api-docs");
    }

    @Test
    @DisplayName("ResourceNotFoundException (subclass) → 404 NOT_FOUND")
    void handlesResourceNotFoundException() {
        when(request.getRequestURI()).thenReturn("/whatever");
        ResourceNotFoundException ex = new ResourceNotFoundException("Submission", "abc-123");

        ResponseEntity<ErrorResponse> response = handler.handleCodeRankException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).contains("Submission").contains("abc-123");
    }

    @Test
    @DisplayName("Generic Exception → 500 with hidden details")
    void handlesGenericException() {
        when(request.getRequestURI()).thenReturn("/swagger-ui.html");
        Exception ex = new IllegalStateException("internal kafka driver leak");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getError()).isEqualTo("Internal Server Error");
        // Generic handler must NOT leak the original message
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(body.getPath()).isEqualTo("/swagger-ui.html");
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("NullPointerException is handled by generic handler with hidden details")
    void handlesNullPointerException() {
        when(request.getRequestURI()).thenReturn("/foo");
        NullPointerException npe = new NullPointerException("npe");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(npe, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }
}