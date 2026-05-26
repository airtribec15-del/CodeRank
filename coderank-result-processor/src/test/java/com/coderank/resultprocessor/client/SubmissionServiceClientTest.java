// src/test/java/com/coderank/resultprocessor/client/SubmissionServiceClientTest.java
package com.coderank.resultprocessor.client;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.coderank.resultprocessor.dto.UpdateSubmissionResultRequest;
import com.coderank.resultprocessor.exception.NonRetryableResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionServiceClient — WebClient + resilience tests")
class SubmissionServiceClientTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private SubmissionServiceClient client;
    private AtomicReference<org.springframework.web.reactive.function.client.ClientRequest> capturedRequest;

    private UUID jobId;
    private UUID submissionId;

    @BeforeEach
    void setUp() {
        // Build a real WebClient backed by a mock ExchangeFunction — this lets us
        // test the entire reactive chain (status handlers, error mapping) without
        // spinning up an HTTP server and without touching production code.
        capturedRequest = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(req -> {
                    capturedRequest.set(req);
                    return exchangeFunction.exchange(req);
                });

        client = new SubmissionServiceClient(builder, "http://coderank-submission:8083");
        jobId = UUID.randomUUID();
        submissionId = UUID.randomUUID();
    }

    private UpdateSubmissionResultRequest sampleRequest() {
        return UpdateSubmissionResultRequest.builder()
                .jobId(jobId)
                .submissionId(submissionId)
                .status(ExecutionStatus.COMPLETED)
                .verdict(Verdict.ACCEPTED)
                .stdout("ok")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(120L)
                .completedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();
    }

    private ClientResponse jsonResponse(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body == null ? "" : body)
                .build();
    }

    /* ---------- 2xx happy path ---------- */

    @Test
    @DisplayName("2xx response → returns normally and PATCHes to correct URI")
    void successfulCall() {
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(jsonResponse(HttpStatus.OK, "")));

        client.updateSubmissionResult(sampleRequest());

        // Verify PATCH method and path
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().method().name()).isEqualTo("PATCH");
        assertThat(capturedRequest.get().url().toString())
                .isEqualTo("http://coderank-submission:8083/api/v1/internal/submissions/result");
    }

    @Test
    @DisplayName("204 No Content is still treated as success")
    void noContentIsSuccess() {
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(jsonResponse(HttpStatus.NO_CONTENT, "")));

        // Should not throw
        client.updateSubmissionResult(sampleRequest());
    }

    /* ---------- 4xx → NonRetryableResultException ---------- */

    @Test
    @DisplayName("400 Bad Request → NonRetryableResultException containing error body")
    void badRequestThrowsNonRetryable() {
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(jsonResponse(HttpStatus.BAD_REQUEST, "invalid payload")));

        assertThatThrownBy(() -> client.updateSubmissionResult(sampleRequest()))
                .isInstanceOf(NonRetryableResultException.class)
                .hasMessageContaining("Submission Service rejected result update")
                .hasMessageContaining(jobId.toString())
                .hasMessageContaining("invalid payload");
    }

    @Test
    @DisplayName("404 Not Found → NonRetryableResultException")
    void notFoundThrowsNonRetryable() {
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(jsonResponse(HttpStatus.NOT_FOUND, "submission missing")));

        assertThatThrownBy(() -> client.updateSubmissionResult(sampleRequest()))
                .isInstanceOf(NonRetryableResultException.class)
                .hasMessageContaining("submission missing");
    }

    @Test
    @DisplayName("409 Conflict → NonRetryableResultException")
    void conflictThrowsNonRetryable() {
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(jsonResponse(HttpStatus.CONFLICT, "already finalized")));

        assertThatThrownBy(() -> client.updateSubmissionResult(sampleRequest()))
                .isInstanceOf(NonRetryableResultException.class);
    }

    /* ---------- 5xx → RuntimeException ---------- */

    @Test
    @DisplayName("500 Internal Server Error → RuntimeException (retryable)")
    void serverErrorThrowsRuntime() {
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "boom")));

        assertThatThrownBy(() -> client.updateSubmissionResult(sampleRequest()))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(NonRetryableResultException.class)
                .hasMessageContaining("Submission Service server error")
                .hasMessageContaining(jobId.toString())
                .hasMessageContaining("boom");
    }

    @Test
    @DisplayName("503 Service Unavailable → RuntimeException (retryable)")
    void serviceUnavailableThrowsRuntime() {
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(jsonResponse(HttpStatus.SERVICE_UNAVAILABLE, "down")));

        assertThatThrownBy(() -> client.updateSubmissionResult(sampleRequest()))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(NonRetryableResultException.class);
    }

    /* ---------- Network failure ---------- */

    @Test
    @DisplayName("Connection failure (exchange Mono.error) → RuntimeException propagates")
    void networkErrorPropagates() {
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        assertThatThrownBy(() -> client.updateSubmissionResult(sampleRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("connection refused");
    }

    /* ---------- Fallback method ---------- */

    @Test
    @DisplayName("Fallback method wraps the cause in a RuntimeException with jobId in message")
    void fallbackWrapsCause() {
        UpdateSubmissionResultRequest request = sampleRequest();
        Throwable cause = new IllegalStateException("circuit open");

        assertThatThrownBy(() -> client.updateResultFallback(request, cause))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("circuit breaker open")
                .hasMessageContaining(jobId.toString())
                .hasCause(cause);
    }

    @Test
    @DisplayName("Fallback method handles null cause message without NPE")
    void fallbackWithNullMessageCause() {
        UpdateSubmissionResultRequest request = sampleRequest();
        Throwable cause = new RuntimeException();  // null message

        assertThatThrownBy(() -> client.updateResultFallback(request, cause))
                .isInstanceOf(RuntimeException.class)
                .hasCause(cause);
    }
}