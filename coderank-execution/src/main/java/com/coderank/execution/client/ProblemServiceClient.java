package com.coderank.execution.client;

import com.coderank.execution.model.TestCaseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Internal HTTP client for fetching test cases from Problem Service.
 * Only called for SUBMIT-type execution jobs (problemId is non-null).
 *
 * Auth model: forwards the originating user's identity via the same headers
 * the API Gateway injects (X-User-Id, X-User-Role). Problem Service's
 * GatewayAuthenticationFilter trusts these headers because Problem Service
 * is only reachable from inside the Docker network (the Gateway blocks
 * /api/v1/internal/** externally — see gateway application.yml).
 */
@Slf4j
@Component
public class ProblemServiceClient {

    // Must match Problem's GatewayAuthenticationFilter constants.
    private static final String HEADER_USER_ID   = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String INTERNAL_ROLE    = "ROLE_USER";

    private final WebClient webClient;

    public ProblemServiceClient(
            WebClient.Builder builder,
            @Value("${problem-service.url}") String problemServiceUrl) {
        this.webClient = builder.baseUrl(problemServiceUrl).build();
    }

    /**
     * Fetches all test cases for the given problem from Problem Service.
     *
     * @param problemId the problem whose test cases to retrieve
     * @param userId    the originating user (forwarded for audit / authz)
     * @return list of test cases
     */
    public List<TestCaseDto> getTestCases(UUID problemId, UUID userId) {
        try {
            List<TestCaseDto> testCases = webClient.get()
                    .uri("/api/v1/internal/problems/{id}/testcases", problemId)
                    .header(HEADER_USER_ID,   userId.toString())
                    .header(HEADER_USER_ROLE, INTERNAL_ROLE)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<TestCaseDto>>() {})
                    .block();

            if (testCases == null) {
                log.warn("Null test cases response from Problem Service for problemId={}", problemId);
                return Collections.emptyList();
            }

            log.debug("Fetched {} test cases for problemId={} userId={}",
                    testCases.size(), problemId, userId);
            return testCases;

        } catch (WebClientResponseException ex) {
            log.error("Problem Service returned {} for problemId={} userId={}: {}",
                    ex.getStatusCode(), problemId, userId, ex.getMessage());
            throw new RuntimeException("Failed to fetch test cases for problem " + problemId, ex);
        } catch (Exception ex) {
            log.error("Unexpected error fetching test cases for problemId={} userId={}: {}",
                    problemId, userId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to fetch test cases for problem " + problemId, ex);
        }
    }
}