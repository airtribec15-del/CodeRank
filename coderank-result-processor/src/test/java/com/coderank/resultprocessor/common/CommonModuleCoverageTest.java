package com.coderank.resultprocessor.common;

import com.coderank.common.config.JacksonConfig;
import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.constants.RedisKeys;
import com.coderank.common.dto.request.ExecuteRequest;
import com.coderank.common.dto.response.ErrorResponse;
import com.coderank.common.dto.response.ExecutionResultResponse;
import com.coderank.common.dto.response.SubmissionDetailResponse;
import com.coderank.common.dto.response.SubmissionResponse;
import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.common.enums.UserRole;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.common.event.StateUpdateEvent;
import com.coderank.common.exception.CodeRankException;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("coderank-common — coverage tests for enums, DTOs, exceptions, constants, config")
class CommonModuleCoverageTest {

    /* =====================================================================
       ENUMS
       ===================================================================== */

    @Nested
    @DisplayName("Language enum")
    class LanguageEnumTests {

        @Test
        @DisplayName("getValue returns lowercase string for all constants")
        void getValueForAll() {
            assertThat(Language.PYTHON.getValue()).isEqualTo("python");
            assertThat(Language.JAVA.getValue()).isEqualTo("java");
            assertThat(Language.JAVASCRIPT.getValue()).isEqualTo("javascript");
            assertThat(Language.CPP.getValue()).isEqualTo("cpp");
        }

        @Test
        @DisplayName("fromValue resolves all known values case-insensitively")
        void fromValueResolves() {
            assertThat(Language.fromValue("python")).isEqualTo(Language.PYTHON);
            assertThat(Language.fromValue("JAVA")).isEqualTo(Language.JAVA);
            assertThat(Language.fromValue("JavaScript")).isEqualTo(Language.JAVASCRIPT);
            assertThat(Language.fromValue("cpp")).isEqualTo(Language.CPP);
        }

        @Test
        @DisplayName("fromValue throws IllegalArgumentException for unknown language")
        void fromValueRejectsUnknown() {
            assertThatThrownBy(() -> Language.fromValue("ruby"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported language");
        }

        @Test
        @DisplayName("values() exposes 4 constants")
        void valuesCount() {
            assertThat(Language.values()).hasSize(4);
        }
    }

    @Nested
    @DisplayName("UserRole enum")
    class UserRoleEnumTests {

        @Test
        @DisplayName("getValue returns ROLE_-prefixed string for both constants")
        void getValueForAll() {
            assertThat(UserRole.ROLE_USER.getValue()).isEqualTo("ROLE_USER");
            assertThat(UserRole.ROLE_ADMIN.getValue()).isEqualTo("ROLE_ADMIN");
        }

        @Test
        @DisplayName("fromString resolves both constants case-insensitively")
        void fromStringResolves() {
            assertThat(UserRole.fromString("ROLE_USER")).isEqualTo(UserRole.ROLE_USER);
            assertThat(UserRole.fromString("role_admin")).isEqualTo(UserRole.ROLE_ADMIN);
        }

        @Test
        @DisplayName("fromString throws IllegalArgumentException for unknown role")
        void fromStringRejectsUnknown() {
            assertThatThrownBy(() -> UserRole.fromString("ROLE_SUPERUSER"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown role");
        }
    }

    @Nested
    @DisplayName("ExecutionStatus & Verdict enums")
    class StatusVerdictEnums {

        @Test
        @DisplayName("ExecutionStatus has all 5 expected constants")
        void executionStatusValues() {
            assertThat(ExecutionStatus.values())
                    .containsExactly(
                            ExecutionStatus.QUEUED,
                            ExecutionStatus.RUNNING,
                            ExecutionStatus.COMPLETED,
                            ExecutionStatus.FAILED,
                            ExecutionStatus.TIMEDOUT);
            // valueOf for branch coverage on enum classes
            assertThat(ExecutionStatus.valueOf("COMPLETED")).isEqualTo(ExecutionStatus.COMPLETED);
        }

        @Test
        @DisplayName("Verdict has all 7 expected constants")
        void verdictValues() {
            assertThat(Verdict.values()).hasSize(7);
            assertThat(Verdict.valueOf("ACCEPTED")).isEqualTo(Verdict.ACCEPTED);
            assertThat(Verdict.valueOf("WRONG_ANSWER")).isEqualTo(Verdict.WRONG_ANSWER);
            assertThat(Verdict.valueOf("TIME_LIMIT_EXCEEDED")).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
            assertThat(Verdict.valueOf("RUNTIME_ERROR")).isEqualTo(Verdict.RUNTIME_ERROR);
            assertThat(Verdict.valueOf("COMPILATION_ERROR")).isEqualTo(Verdict.COMPILATION_ERROR);
            assertThat(Verdict.valueOf("INTERNAL_ERROR")).isEqualTo(Verdict.INTERNAL_ERROR);
            assertThat(Verdict.valueOf("PENDING")).isEqualTo(Verdict.PENDING);
        }
    }

    /* =====================================================================
       CONSTANTS — KafkaTopics & RedisKeys
       ===================================================================== */

    @Nested
    @DisplayName("KafkaTopics & RedisKeys constants")
    class ConstantsTests {

        @Test
        @DisplayName("KafkaTopics expose all expected topic names")
        void kafkaTopics() {
            assertThat(KafkaTopics.EXECUTION_REQUESTS).isEqualTo("code.execution.requests");
            assertThat(KafkaTopics.EXECUTION_REQUESTS_DLT).isEqualTo("code.execution.requests-dlt");
            assertThat(KafkaTopics.EXECUTION_RESULTS).isEqualTo("code.execution.results");
            assertThat(KafkaTopics.EXECUTION_RESULTS_DLT).isEqualTo("code.execution.results-dlt");
            assertThat(KafkaTopics.STATE_UPDATE_EVENTS).isEqualTo("state-update-events");
            assertThat(KafkaTopics.STATE_UPDATE_EVENTS_DLT).isEqualTo("state-update-events-dlt");
        }

        @Test
        @DisplayName("RedisKeys prefixes are stable")
        void redisKeyPrefixes() {
            assertThat(RedisKeys.JOB_STATUS_PREFIX).isEqualTo("job_status:");
            assertThat(RedisKeys.JOB_RUNNING_PREFIX).isEqualTo("job_running:");
            assertThat(RedisKeys.JWT_BLACKLIST_PREFIX).isEqualTo("jwt_blacklist:");
            assertThat(RedisKeys.RATE_LIMIT_PREFIX).isEqualTo("rate_limit:");
            assertThat(RedisKeys.RATE_LIMIT_IP_PREFIX).isEqualTo("rate_limit:ip:");
        }

        @Test
        @DisplayName("RedisKeys factory methods produce correctly-prefixed keys")
        void redisKeyFactories() {
            assertThat(RedisKeys.jobStatusKey("abc")).isEqualTo("job_status:abc");
            assertThat(RedisKeys.jobRunningKey("abc")).isEqualTo("job_running:abc");
            assertThat(RedisKeys.jwtBlacklistKey("jti-1")).isEqualTo("jwt_blacklist:jti-1");
            assertThat(RedisKeys.rateLimitIpKey("1.2.3.4")).isEqualTo("rate_limit:ip:1.2.3.4");
            assertThat(RedisKeys.rateLimitUserKey("user-7")).isEqualTo("rate_limit:user-7");
        }
    }

    /* =====================================================================
       EXCEPTIONS
       ===================================================================== */

    @Nested
    @DisplayName("Exception hierarchy")
    class ExceptionTests {

        @Test
        @DisplayName("CodeRankException 3-arg ctor preserves all fields")
        void codeRankBasicCtor() {
            CodeRankException ex = new CodeRankException("msg", HttpStatus.I_AM_A_TEAPOT, "TEAPOT");
            assertThat(ex.getMessage()).isEqualTo("msg");
            assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
            assertThat(ex.getErrorCode()).isEqualTo("TEAPOT");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("CodeRankException 4-arg ctor preserves cause")
        void codeRankWithCauseCtor() {
            Throwable root = new IllegalStateException("root");
            CodeRankException ex = new CodeRankException(
                    "msg", root, HttpStatus.BAD_GATEWAY, "BAD_GW");
            assertThat(ex.getMessage()).isEqualTo("msg");
            assertThat(ex.getCause()).isSameAs(root);
            assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(ex.getErrorCode()).isEqualTo("BAD_GW");
        }

        @Test
        @DisplayName("InvalidRequestException maps to 400 / INVALID_REQUEST")
        void invalidRequestException() {
            InvalidRequestException ex = new InvalidRequestException("bad");
            assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getErrorCode()).isEqualTo("INVALID_REQUEST");
            assertThat(ex.getMessage()).isEqualTo("bad");
        }

        @Test
        @DisplayName("ResourceNotFoundException maps to 404 / RESOURCE_NOT_FOUND with composed message")
        void resourceNotFoundException() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Problem", "p-1");
            assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(ex.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
            assertThat(ex.getMessage())
                    .contains("Problem")
                    .contains("p-1")
                    .contains("not found");
        }
    }

    /* =====================================================================
       ErrorResponse DTO
       ===================================================================== */

    @Nested
    @DisplayName("ErrorResponse DTO")
    class ErrorResponseTests {

        @Test
        @DisplayName("of() factory populates all required fields and a non-null timestamp")
        void factoryOf() {
            Instant before = Instant.now();
            ErrorResponse er = ErrorResponse.of(400, "Bad Request", "msg", "/path");
            Instant after = Instant.now();

            assertThat(er.getStatus()).isEqualTo(400);
            assertThat(er.getError()).isEqualTo("Bad Request");
            assertThat(er.getMessage()).isEqualTo("msg");
            assertThat(er.getPath()).isEqualTo("/path");
            assertThat(er.getTimestamp()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
            assertThat(er.getFieldErrors()).isNull();
        }

        @Test
        @DisplayName("Builder with fieldErrors works and equals/hashCode reflect content")
        void builderWithFieldErrors() {
            ErrorResponse.FieldError fe = ErrorResponse.FieldError.builder()
                    .field("language").message("must not be null").build();

            ErrorResponse er = ErrorResponse.builder()
                    .status(422).error("Unprocessable").message("m").path("/x")
                    .timestamp(Instant.EPOCH)
                    .fieldErrors(List.of(fe))
                    .build();

            assertThat(er.getFieldErrors()).hasSize(1);
            assertThat(er.getFieldErrors().get(0).getField()).isEqualTo("language");
            assertThat(er.getFieldErrors().get(0).getMessage()).isEqualTo("must not be null");

            ErrorResponse copy = ErrorResponse.builder()
                    .status(422).error("Unprocessable").message("m").path("/x")
                    .timestamp(Instant.EPOCH)
                    .fieldErrors(List.of(fe))
                    .build();
            assertThat(er).isEqualTo(copy).hasSameHashCodeAs(copy);
        }

        @Test
        @DisplayName("FieldError no-args ctor + setters work")
        void fieldErrorNoArgsAndSetters() {
            ErrorResponse.FieldError fe = new ErrorResponse.FieldError();
            fe.setField("f");
            fe.setMessage("m");
            assertThat(fe.getField()).isEqualTo("f");
            assertThat(fe.getMessage()).isEqualTo("m");
        }
    }

    /* =====================================================================
       Other DTOs / Events — builder smoke + getter/setter coverage
       ===================================================================== */

    @Nested
    @DisplayName("Request / Response DTOs and Kafka events")
    class DtoEventTests {

        @Test
        @DisplayName("ExecuteRequest builder/setters cover all fields")
        void executeRequest() {
            ExecuteRequest r = ExecuteRequest.builder()
                    .language(Language.JAVA)
                    .sourceCode("class A {}")
                    .stdinInput("input")
                    .build();
            assertThat(r.getLanguage()).isEqualTo(Language.JAVA);
            assertThat(r.getSourceCode()).isEqualTo("class A {}");
            assertThat(r.getStdinInput()).isEqualTo("input");

            ExecuteRequest empty = new ExecuteRequest();
            empty.setLanguage(Language.PYTHON);
            assertThat(empty.getLanguage()).isEqualTo(Language.PYTHON);
        }

        @Test
        @DisplayName("SubmissionResponse and SubmissionDetailResponse builders work")
        void submissionResponses() {
            UUID submissionId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            Instant now = Instant.parse("2026-01-01T10:00:00Z");

            SubmissionResponse sr = SubmissionResponse.builder()
                    .submissionId(submissionId).jobId(jobId)
                    .language(Language.PYTHON).status(ExecutionStatus.QUEUED)
                    .createdAt(now).build();
            assertThat(sr.getSubmissionId()).isEqualTo(submissionId);
            assertThat(sr.getStatus()).isEqualTo(ExecutionStatus.QUEUED);

            SubmissionDetailResponse sdr = SubmissionDetailResponse.builder()
                    .submissionId(submissionId).jobId(jobId)
                    .language(Language.JAVA).status(ExecutionStatus.COMPLETED)
                    .sourceCode("src").stdinInput("in")
                    .stdout("out").stderr("err")
                    .exitCode(0).executionTimeMs(50L)
                    .createdAt(now).completedAt(now)
                    .build();
            assertThat(sdr.getSourceCode()).isEqualTo("src");
            assertThat(sdr.getCompletedAt()).isEqualTo(now);
            assertThat(sdr.getExecutionTimeMs()).isEqualTo(50L);
        }

        @Test
        @DisplayName("ExecutionResultResponse builder populates all fields")
        void executionResultResponse() {
            UUID submissionId = UUID.randomUUID();
            Instant now = Instant.now();
            ExecutionResultResponse rr = ExecutionResultResponse.builder()
                    .submissionId(submissionId)
                    .jobId(UUID.randomUUID())
                    .language(Language.CPP)
                    .status(ExecutionStatus.COMPLETED)
                    .stdout("o").stderr("e").exitCode(0).executionTimeMs(1L)
                    .createdAt(now).completedAt(now)
                    .build();
            assertThat(rr.getSubmissionId()).isEqualTo(submissionId);
            assertThat(rr.getLanguage()).isEqualTo(Language.CPP);
        }

        @Test
        @DisplayName("CodeExecutionRequestEvent builder + setters")
        void codeExecutionRequestEvent() {
            CodeExecutionRequestEvent ev = CodeExecutionRequestEvent.builder()
                    .jobId(UUID.randomUUID())
                    .submissionId(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .problemId(UUID.randomUUID())
                    .language(Language.JAVA)
                    .sourceCode("src")
                    .stdinInput("in")
                    .timeoutSeconds(5)
                    .submittedAt(Instant.now())
                    .build();
            assertThat(ev.getTimeoutSeconds()).isEqualTo(5);

            CodeExecutionRequestEvent empty = new CodeExecutionRequestEvent();
            empty.setTimeoutSeconds(10);
            assertThat(empty.getTimeoutSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("CodeExecutionResultEvent builder + setters")
        void codeExecutionResultEvent() {
            CodeExecutionResultEvent ev = CodeExecutionResultEvent.builder()
                    .jobId(UUID.randomUUID())
                    .submissionId(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .problemId(UUID.randomUUID())
                    .verdict(Verdict.ACCEPTED)
                    .status(ExecutionStatus.COMPLETED)
                    .stdout("o").stderr("e").exitCode(0).executionTimeMs(1L)
                    .completedAt(Instant.now())
                    .build();
            assertThat(ev.getVerdict()).isEqualTo(Verdict.ACCEPTED);

            CodeExecutionResultEvent empty = new CodeExecutionResultEvent();
            empty.setStatus(ExecutionStatus.FAILED);
            assertThat(empty.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        }

        @Test
        @DisplayName("StateUpdateEvent builder + setters")
        void stateUpdateEvent() {
            StateUpdateEvent ev = StateUpdateEvent.builder()
                    .jobId(UUID.randomUUID())
                    .submissionId(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .problemId(UUID.randomUUID())
                    .verdict(Verdict.WRONG_ANSWER)
                    .completedAt(Instant.now())
                    .build();
            assertThat(ev.getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);

            StateUpdateEvent empty = new StateUpdateEvent();
            empty.setVerdict(Verdict.ACCEPTED);
            assertThat(empty.getVerdict()).isEqualTo(Verdict.ACCEPTED);
        }
    }

    /* =====================================================================
       JacksonConfig — ObjectMapper round-trip
       ===================================================================== */

    @Nested
    @DisplayName("JacksonConfig")
    class JacksonConfigTests {

        @Test
        @DisplayName("ObjectMapper bean serializes Instant as ISO string (not timestamp)")
        void objectMapperSerializesInstantAsIso() throws Exception {
            ObjectMapper mapper = new JacksonConfig().objectMapper();

            Instant instant = Instant.parse("2026-01-01T10:00:00Z");
            String json = mapper.writeValueAsString(instant);

            // ISO-8601 string, not a numeric timestamp
            assertThat(json).contains("2026-01-01T10:00:00").doesNotContain("E");
            assertThat(json.charAt(0)).isEqualTo('"');
        }

        @Test
        @DisplayName("ObjectMapper bean ignores unknown JSON properties on deserialization")
        void objectMapperIgnoresUnknown() throws Exception {
            ObjectMapper mapper = new JacksonConfig().objectMapper();

            String json = "{\"language\":\"java\",\"sourceCode\":\"x\",\"weirdField\":42}";
            ExecuteRequest req = mapper.readValue(json, ExecuteRequest.class);

            assertThat(req.getLanguage()).isEqualTo(Language.JAVA);
            assertThat(req.getSourceCode()).isEqualTo("x");
        }
    }
}