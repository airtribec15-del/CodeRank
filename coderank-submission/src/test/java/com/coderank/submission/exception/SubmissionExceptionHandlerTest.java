package com.coderank.submission.exception;

import com.coderank.common.dto.response.ErrorResponse;
import com.coderank.common.exception.CodeRankException;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SubmissionExceptionHandler")
class SubmissionExceptionHandlerTest {

    private final SubmissionExceptionHandler handler = new SubmissionExceptionHandler();
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/submissions");
    }

    // ------------------------------------------------------------------ //
    //  CodeRankException                                                   //
    // ------------------------------------------------------------------ //

    @Nested @DisplayName("handleCodeRankException")
    class HandleCodeRankException {

        @Test
        @DisplayName("maps 400 InvalidRequestException correctly")
        void shouldMap400ForInvalidRequest() {
            InvalidRequestException ex = new InvalidRequestException("Bad input");

            ResponseEntity<ErrorResponse> response = handler.handleCodeRankException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(400);
            assertThat(response.getBody().getError()).isEqualTo("INVALID_REQUEST");
            assertThat(response.getBody().getMessage()).isEqualTo("Bad input");
            assertThat(response.getBody().getPath()).isEqualTo("/api/v1/submissions");
        }

        @Test
        @DisplayName("maps 404 ResourceNotFoundException correctly")
        void shouldMap404ForResourceNotFound() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Submission", "abc-123");

            ResponseEntity<ErrorResponse> response = handler.handleCodeRankException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().getStatus()).isEqualTo(404);
            assertThat(response.getBody().getError()).isEqualTo("RESOURCE_NOT_FOUND");
        }

        @Test
        @DisplayName("preserves custom HTTP status from CodeRankException")
        void shouldPreserveCustomStatus() {
            CodeRankException ex = new CodeRankException(
                    "Rate limited", HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");

            ResponseEntity<ErrorResponse> response = handler.handleCodeRankException(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.getBody().getStatus()).isEqualTo(429);
        }
    }

    // ------------------------------------------------------------------ //
    //  MethodArgumentNotValidException                                     //
    // ------------------------------------------------------------------ //

    @Nested @DisplayName("handleValidation")
    class HandleValidation {

        @Test
        @DisplayName("returns 400 with fieldErrors list")
        void shouldReturn400WithFieldErrors() {
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);

            FieldError fieldError = new FieldError("submitRequest", "language",
                    "Language must not be null");
            when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getError()).isEqualTo("VALIDATION_FAILED");
            assertThat(response.getBody().getFieldErrors()).hasSize(1);
            assertThat(response.getBody().getFieldErrors().get(0).getField()).isEqualTo("language");
            assertThat(response.getBody().getFieldErrors().get(0).getMessage())
                    .isEqualTo("Language must not be null");
        }

        @Test
        @DisplayName("handles multiple field errors")
        void shouldHandleMultipleFieldErrors() {
            MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
            BindingResult bindingResult = mock(BindingResult.class);

            List<FieldError> errors = List.of(
                    new FieldError("r", "language", "Language must not be null"),
                    new FieldError("r", "sourceCode", "Source code must not be blank"),
                    new FieldError("r", "problemId", "Problem ID must not be null")
            );
            when(bindingResult.getFieldErrors()).thenReturn(errors);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

            assertThat(response.getBody().getFieldErrors()).hasSize(3);
        }
    }

    // ------------------------------------------------------------------ //
    //  Generic Exception                                                   //
    // ------------------------------------------------------------------ //

    @Nested @DisplayName("handleGeneric")
    class HandleGeneric {

        @Test
        @DisplayName("returns 500 for unexpected RuntimeException")
        void shouldReturn500ForUnexpectedException() {
            RuntimeException ex = new RuntimeException("Unexpected NullPointerException");

            ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getStatus()).isEqualTo(500);
            assertThat(response.getBody().getError()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        }

        @Test
        @DisplayName("includes request URI in the error response path")
        void shouldIncludeRequestUriInPath() {
            when(request.getRequestURI()).thenReturn("/api/v1/execute");
            RuntimeException ex = new RuntimeException("boom");

            ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

            assertThat(response.getBody().getPath()).isEqualTo("/api/v1/execute");
        }
    }
}
