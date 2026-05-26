package com.coderank.execution.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TestCaseDto} field-name JSON mapping.
 *
 * <p>Critical contract: Problem Service serializes test cases with keys
 * {@code "expected"} (NOT {@code "expectedOutput"}) and {@code "isSample"}
 * (NOT {@code "hidden"}). If these mappings ever silently drift, every
 * submission's verdict becomes WRONG_ANSWER.
 */
@DisplayName("TestCaseDto JSON mapping")
class TestCaseDtoTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    @DisplayName("deserializes JSON key 'expected' into expectedOutput field")
    void deserializesExpectedKey() throws Exception {
        String json = """
                {
                  "id": "00000000-0000-0000-0000-000000000001",
                  "input": "1 2",
                  "expected": "3",
                  "isSample": true
                }
                """;

        TestCaseDto dto = mapper.readValue(json, TestCaseDto.class);

        assertThat(dto.getId())
                .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(dto.getInput()).isEqualTo("1 2");
        assertThat(dto.getExpectedOutput()).isEqualTo("3");
        assertThat(dto.isHidden()).isTrue();
    }

    @Test
    @DisplayName("deserializes 'isSample=false' into hidden=false")
    void deserializesIsSampleFalse() throws Exception {
        String json = """
                {
                  "id": "00000000-0000-0000-0000-000000000002",
                  "input": "a",
                  "expected": "b",
                  "isSample": false
                }
                """;

        TestCaseDto dto = mapper.readValue(json, TestCaseDto.class);

        assertThat(dto.isHidden()).isFalse();
    }

    @Test
    @DisplayName("leaves expectedOutput null when 'expected' is missing")
    void missingExpectedKeyYieldsNull() throws Exception {
        String json = """
                {
                  "id": "00000000-0000-0000-0000-000000000003",
                  "input": "x",
                  "isSample": false
                }
                """;

        TestCaseDto dto = mapper.readValue(json, TestCaseDto.class);

        assertThat(dto.getExpectedOutput()).isNull();
    }

    @Test
    @DisplayName("ignores unknown JSON properties without throwing")
    void ignoresUnknownProperties() throws Exception {
        String json = """
                {
                  "id": "00000000-0000-0000-0000-000000000004",
                  "input": "x",
                  "expected": "y",
                  "isSample": true,
                  "extraField": "should-be-ignored",
                  "weight": 100
                }
                """;

        TestCaseDto dto = mapper.readValue(json, TestCaseDto.class);

        assertThat(dto.getExpectedOutput()).isEqualTo("y");
        assertThat(dto.isHidden()).isTrue();
    }

    @Test
    @DisplayName("builder produces a fully-populated DTO")
    void builderRoundTrip() {
        UUID id = UUID.randomUUID();
        TestCaseDto dto = TestCaseDto.builder()
                .id(id)
                .input("in")
                .expectedOutput("out")
                .hidden(true)
                .build();

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getInput()).isEqualTo("in");
        assertThat(dto.getExpectedOutput()).isEqualTo("out");
        assertThat(dto.isHidden()).isTrue();
    }

    @Test
    @DisplayName("no-args constructor yields a blank DTO")
    void noArgsConstructor() {
        TestCaseDto dto = new TestCaseDto();

        assertThat(dto.getId()).isNull();
        assertThat(dto.getInput()).isNull();
        assertThat(dto.getExpectedOutput()).isNull();
        assertThat(dto.isHidden()).isFalse();
    }
}