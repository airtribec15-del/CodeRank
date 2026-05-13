package com.coderank.gateway.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(FallbackController.class)
@ActiveProfiles("test")
class FallbackControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Should return 503 with correct body for auth fallback")
    void shouldReturn503ForAuthFallback() {
        webTestClient.post()
                .uri("/fallback/auth")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    @DisplayName("Should return 503 with correct body for submission fallback")
    void shouldReturn503ForSubmissionFallback() {
        webTestClient.get()
                .uri("/fallback/submission")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    @DisplayName("Auth fallback message should mention Auth Service")
    void authFallbackShouldMentionAuthService() {
        webTestClient.get()
                .uri("/fallback/auth")
                .exchange()
                .expectBody()
                .jsonPath("$.message").value(msg ->
                        org.assertj.core.api.Assertions.assertThat(msg.toString())
                                .containsIgnoringCase("Auth Service"));
    }

    @Test
    @DisplayName("Submission fallback message should mention Submission Service")
    void submissionFallbackShouldMentionSubmissionService() {
        webTestClient.get()
                .uri("/fallback/submission")
                .exchange()
                .expectBody()
                .jsonPath("$.message").value(msg ->
                        org.assertj.core.api.Assertions.assertThat(msg.toString())
                                .containsIgnoringCase("Submission Service"));
    }
}