package com.coderank.resultprocessor.client;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.coderank.resultprocessor.dto.UpdateSubmissionResultRequest;
import com.coderank.resultprocessor.exception.NonRetryableResultException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal HTTP client that calls Submission Service to persist the final result.
 *
 * <h2>Endpoint</h2>
 * {@code PATCH /api/v1/internal/submissions/result}
 *
 * <h2>Resilience</h2>
 * <ul>
 *   <li>Resilience4j {@code @Retry} — retries transient network errors up to 3 times</li>
 *   <li>Resilience4j {@code @CircuitBreaker} — opens after 50% failure rate,
 *       preventing cascade failures if Submission Service is down</li>
 * </ul>
 *
 * <h2>Non-Retryable Errors</h2>
 * HTTP 4xx responses (bad request, not found) are NOT retried — they indicate
 * a data contract violation. A {@link NonRetryableResultException} is thrown,
 * which bypasses {@code @RetryableTopic} and routes the Kafka message to the DLT.
 */
@Slf4j
@Component
public class SubmissionServiceClient {

    private final WebClient webClient;

    public SubmissionServiceClient(
            WebClient.Builder builder,
            @Value("${submission-service.url}") String submissionServiceUrl) {
        this.webClient = builder.baseUrl(submissionServiceUrl).build();
    }

    /**
     * Sends the final execution result to Submission Service for DB persistence.
     *
     * @param request the result payload to persist
     * @throws NonRetryableResultException if the Submission Service returns a 4xx error
     * @throws RuntimeException            on 5xx or network errors (retryable via @Retry)
     */
    @CircuitBreaker(name = "submissionServiceClient", fallbackMethod = "updateResultFallback")
    @Retry(name = "submissionServiceClient")
    public void updateSubmissionResult(UpdateSubmissionResultRequest request) {
        log.debug("Calling Submission Service PATCH /internal/submissions/result for jobId={}",
                request.getJobId());

        webClient.patch()
                .uri("/api/v1/internal/submissions/result")
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new NonRetryableResultException(
                                        "Submission Service rejected result update for jobId=" +
                                                request.getJobId() + " — " + errorBody
                                )))
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException(
                                        "Submission Service server error for jobId=" +
                                                request.getJobId() + " — " + errorBody
                                )))
                )
                .toBodilessEntity()
                .block();

        log.info("Submission Service updated successfully for jobId={} submissionId={}",
                request.getJobId(), request.getSubmissionId());
    }

    /**
     * Circuit breaker fallback — called when Submission Service is unavailable.
     * Throws RuntimeException so the Kafka consumer can retry via @RetryableTopic.
     */
    public void updateResultFallback(UpdateSubmissionResultRequest request, Throwable t) {
        log.error("Circuit OPEN: Submission Service unavailable for jobId={} — {}",
                request.getJobId(), t.getMessage());
        throw new RuntimeException(
                "Submission Service circuit breaker open for jobId=" + request.getJobId(), t);
    }
}