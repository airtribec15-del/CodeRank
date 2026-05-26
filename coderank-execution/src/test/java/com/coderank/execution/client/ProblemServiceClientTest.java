package com.coderank.execution.client;

import com.coderank.execution.model.TestCaseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProblemServiceClient}.
 *
 * <p>The WebClient fluent chain
 * {@code webClient.get().uri(...).retrieve().bodyToMono(...).block()}
 * involves wildcard-generic return types ({@code RequestHeadersSpec<?>}) that
 * confuse Mockito's {@code when(...).thenReturn(...)} type inference.
 * We therefore use {@code doReturn(...).when(...)} for the steps whose
 * return type involves wildcards — it bypasses the generic type check while
 * remaining a fully type-safe Mockito idiom.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemServiceClient")
class ProblemServiceClientTest {

    @Mock private WebClient.Builder webClientBuilder;
    @Mock private WebClient webClient;

    // Raw types intentionally — WebClient's fluent API uses wildcard generics
    // that cannot be mocked through `when(...).thenReturn(...)` directly.
    @Mock @SuppressWarnings("rawtypes") private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock @SuppressWarnings("rawtypes") private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private ProblemServiceClient client;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        client = new ProblemServiceClient(webClientBuilder, "http://localhost:8082");

        // Use doReturn(...) to avoid generics issues on the fluent chain.
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec)
                .uri(anyString(), any(Object[].class));
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
    }

    // ── Happy path ──────────────────────────────────────────────────────

    @Test
    @DisplayName("returns the list of test cases on a 2xx response")
    void returnsTestCasesOnSuccess() {
        UUID problemId = UUID.randomUUID();
        List<TestCaseDto> stub = List.of(
                TestCaseDto.builder().id(UUID.randomUUID())
                        .input("1 2").expectedOutput("3").hidden(false).build(),
                TestCaseDto.builder().id(UUID.randomUUID())
                        .input("4 5").expectedOutput("9").hidden(true).build()
        );
        doReturn(Mono.just(stub)).when(responseSpec)
                .bodyToMono(any(ParameterizedTypeReference.class));

        List<TestCaseDto> result = client.getTestCases(problemId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getInput()).isEqualTo("1 2");
        assertThat(result.get(0).getExpectedOutput()).isEqualTo("3");
        assertThat(result.get(1).isHidden()).isTrue();

        verify(requestHeadersUriSpec).uri(
                eq("/api/v1/internal/problems/{id}/testcases"),
                eq(problemId));
    }

    @Test
    @DisplayName("returns an empty list when WebClient body is null")
    void returnsEmptyListOnNullBody() {
        doReturn(Mono.empty()).when(responseSpec)
                .bodyToMono(any(ParameterizedTypeReference.class));

        List<TestCaseDto> result = client.getTestCases(UUID.randomUUID());

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("returns an empty list when the response body is an empty list")
    void returnsEmptyListWhenServerReturnsEmptyList() {
        doReturn(Mono.just(List.of())).when(responseSpec)
                .bodyToMono(any(ParameterizedTypeReference.class));

        List<TestCaseDto> result = client.getTestCases(UUID.randomUUID());

        assertThat(result).isNotNull().isEmpty();
    }

    // ── Error paths ─────────────────────────────────────────────────────

    @Test
    @DisplayName("wraps WebClientResponseException (404) in a RuntimeException")
    void wraps4xxInRuntimeException() {
        UUID problemId = UUID.randomUUID();
        WebClientResponseException notFound = WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null);

        doReturn(Mono.error(notFound)).when(responseSpec)
                .bodyToMono(any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.getTestCases(problemId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch test cases")
                .hasMessageContaining(problemId.toString())
                .hasCauseInstanceOf(WebClientResponseException.class);
    }

    @Test
    @DisplayName("wraps 5xx WebClientResponseException in a RuntimeException")
    void wraps5xxInRuntimeException() {
        WebClientResponseException serverError = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Server Error", null, null, null);

        doReturn(Mono.error(serverError)).when(responseSpec)
                .bodyToMono(any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.getTestCases(UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch test cases");
    }

    @Test
    @DisplayName("wraps any other unexpected exception in a RuntimeException")
    void wrapsUnexpectedExceptionInRuntimeException() {
        doReturn(Mono.error(new IllegalStateException("connection reset")))
                .when(responseSpec).bodyToMono(any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.getTestCases(UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch test cases");
    }
}