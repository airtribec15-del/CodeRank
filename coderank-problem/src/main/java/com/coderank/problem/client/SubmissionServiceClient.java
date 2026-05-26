package com.coderank.problem.client;

import com.coderank.common.dto.response.SubmissionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class SubmissionServiceClient {

    // Must match PreAuthenticatedUserFilter.HEADER_USER_ID in coderank-submission
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_ROLE    = "X-User-Role";

    private final WebClient webClient;

    public SubmissionServiceClient(
            @Value("${submission-service.url}") String submissionServiceUrl,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(submissionServiceUrl)
                .build();
    }

    public SubmissionResponse forwardSubmission(
            SubmitCodeRequest request,
            UUID userId,
            String userRole) {

        log.info("Forwarding submission to SubmissionService: userId={} problemId={} role={}",
                userId, request.getProblemId(), userRole);

        return webClient.post()
                .uri("/api/v1/submissions")
                .header(HEADER_USER_ID, userId.toString())
                .header(HEADER_ROLE, userRole)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> {
                    HttpStatus status = HttpStatus.valueOf(clientResponse.statusCode().value());

                    if (status == HttpStatus.TOO_MANY_REQUESTS) {
                        return Mono.error(new SubmissionRateLimitException(
                                "Rate limit exceeded for userId: " + userId));
                    }

                    return clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                log.error("Submission Service responded {} body={}", status, body);
                                if (status.is4xxClientError()) {
                                    return Mono.error(new SubmissionForwardException(
                                            "Submission Service rejected request (" + status + "): " + body));
                                }
                                return Mono.error(new SubmissionForwardException(
                                        "Submission Service internal error (" + status
                                                + ") for userId: " + userId + " body=" + body));
                            });
                })
                .bodyToMono(SubmissionResponse.class)
                .block(Duration.ofSeconds(10));
    }
}