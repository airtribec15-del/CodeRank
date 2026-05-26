package com.coderank.execution.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Local DTO representing a single test case received from Problem Service.
 *
 * <p>Maps directly to the JSON returned by
 * {@code GET /api/v1/internal/problems/{id}/testcases},
 * which serializes {@code InternalTestCaseResponse} objects.</p>
 *
 * <p><strong>Field alignment (CRITICAL):</strong>
 * {@code InternalTestCaseResponse.expected} serializes as JSON key {@code "expected"}.
 * This DTO must deserialize that exact key — hence {@code @JsonProperty("expected")}
 * on {@code expectedOutput}. Without this annotation the field is silently null
 * (Jackson ignores unknown properties), causing every verdict to be WRONG_ANSWER.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseDto {

    private UUID id;

    /** Input to feed via stdin. Maps to {@code InternalTestCaseResponse.input}. */
    private String input;

    /**
     * Expected stdout output for trimmed comparison.
     * Explicitly mapped from JSON key {@code "expected"} to match
     * {@code InternalTestCaseResponse.expected} serialized by Problem Service.
     */
    @JsonProperty("expected")
    private String expectedOutput;

    /**
     * Whether this test case is a sample (visible to users).
     * Maps to {@code InternalTestCaseResponse.isSample}.
     * {@code @JsonProperty} required because Jackson serializes boolean
     * getters named {@code isSample()} as {@code "sample"} by default
     * in some configurations — explicit mapping ensures correctness.
     */
    @JsonProperty("isSample")
    private boolean hidden;
}