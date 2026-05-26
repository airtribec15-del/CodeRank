package com.coderank.execution.service;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.execution.client.ProblemServiceClient;
import com.coderank.execution.docker.DockerSandboxRunner;
import com.coderank.execution.model.ExecutionConfig;
import com.coderank.execution.model.ExecutionResult;
import com.coderank.execution.model.TestCaseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CodeExecutionService}.
 *
 * <p>Production behavior under test:
 * <ul>
 *   <li>{@code executeAsync} routes to {@code executeRun} (problemId == null)
 *       or {@code executeSubmit} (problemId != null).</li>
 *   <li>Redis is updated to RUNNING before sandbox call, then to final status.</li>
 *   <li>Kafka result event is published with jobId as the key on
 *       {@link KafkaTopics#EXECUTION_RESULTS}.</li>
 *   <li>SUBMIT-type computes a {@link Verdict} by comparing trimmed stdout
 *       against each test case in order, short-circuiting on TLE / RE / WA.</li>
 *   <li>Any uncaught exception in the pipeline triggers
 *       {@code publishFailedResult} with {@link Verdict#INTERNAL_ERROR}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CodeExecutionService")
class CodeExecutionServiceTest {

    @Mock private DockerSandboxRunner sandboxRunner;
    @Mock private LanguageConfigResolver languageConfigResolver;
    @Mock private KafkaTemplate<String, CodeExecutionResultEvent> resultKafkaTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ProblemServiceClient problemServiceClient;

    @InjectMocks
    private CodeExecutionService service;

    @BeforeEach
    void setUp() {
        // Lenient: not every test triggers a Redis or language resolution call
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(languageConfigResolver.resolve(any(Language.class))).thenReturn(
                new LanguageConfigResolver.LanguageProfile(
                        "python:3.11-slim", "solution.py", "python3 /code/solution.py"));
    }

    // ────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────

    private CodeExecutionRequestEvent runEvent(Language language, String stdin) {
        return CodeExecutionRequestEvent.builder()
                .jobId(UUID.randomUUID())
                .submissionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .problemId(null) // RUN-type
                .language(language)
                .sourceCode("print('hello')")
                .stdinInput(stdin)
                .timeoutSeconds(10)
                .submittedAt(Instant.now())
                .build();
    }

    private CodeExecutionRequestEvent submitEvent(Language language) {
        return CodeExecutionRequestEvent.builder()
                .jobId(UUID.randomUUID())
                .submissionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .problemId(UUID.randomUUID()) // SUBMIT-type
                .language(language)
                .sourceCode("print('hello')")
                .stdinInput(null)
                .timeoutSeconds(10)
                .submittedAt(Instant.now())
                .build();
    }

    private ExecutionResult execResult(ExecutionStatus status, String stdout,
                                       String stderr, int exitCode, long timeMs) {
        return ExecutionResult.builder()
                .status(status)
                .stdout(stdout)
                .stderr(stderr)
                .exitCode(exitCode)
                .executionTimeMs(timeMs)
                .completedAt(Instant.now())
                .build();
    }

    private TestCaseDto tc(String input, String expected) {
        return TestCaseDto.builder()
                .id(UUID.randomUUID())
                .input(input)
                .expectedOutput(expected)
                .hidden(false)
                .build();
    }

    // ────────────────────────────────────────────────────────────────
    //  executeAsync — RUN-type (problemId == null)
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeAsync — RUN-type")
    class RunType {

        @Test
        @DisplayName("publishes COMPLETED event with verdict=null and exitCode=0")
        void runHappyPath() {
            CodeExecutionRequestEvent req = runEvent(Language.PYTHON, "5");
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.COMPLETED, "120", "", 0, 50L));

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(eq(KafkaTopics.EXECUTION_RESULTS),
                    eq(req.getJobId().toString()), captor.capture());

            CodeExecutionResultEvent published = captor.getValue();
            assertThat(published.getJobId()).isEqualTo(req.getJobId());
            assertThat(published.getSubmissionId()).isEqualTo(req.getSubmissionId());
            assertThat(published.getUserId()).isEqualTo(req.getUserId());
            assertThat(published.getProblemId()).isNull();
            assertThat(published.getVerdict()).isNull();
            assertThat(published.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(published.getStdout()).isEqualTo("120");
            assertThat(published.getExitCode()).isZero();
        }

        @Test
        @DisplayName("publishes FAILED status when exitCode is non-zero")
        void runWithFailure() {
            CodeExecutionRequestEvent req = runEvent(Language.JAVASCRIPT, "");
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.FAILED, "", "ReferenceError", 1, 30L));

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(captor.getValue().getStderr()).contains("ReferenceError");
            assertThat(captor.getValue().getVerdict()).isNull();
        }

        @Test
        @DisplayName("publishes TIMEDOUT status when executionTimeMs >= timeoutSeconds*1000")
        void runWithTle() {
            CodeExecutionRequestEvent req = runEvent(Language.CPP, null);
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.TIMEDOUT, "", "Timed out", 1, 10_000L));

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ExecutionStatus.TIMEDOUT);
        }

        @Test
        @DisplayName("builds ExecutionConfig from LanguageProfile and request fields")
        void runBuildsConfigFromProfile() {
            CodeExecutionRequestEvent req = runEvent(Language.PYTHON, "stdin-data");
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.COMPLETED, "ok", "", 0, 10L));

            service.executeAsync(req);

            ArgumentCaptor<ExecutionConfig> captor = ArgumentCaptor.forClass(ExecutionConfig.class);
            verify(sandboxRunner).run(captor.capture());
            ExecutionConfig cfg = captor.getValue();
            assertThat(cfg.getDockerImage()).isEqualTo("python:3.11-slim");
            assertThat(cfg.getSourceFileName()).isEqualTo("solution.py");
            assertThat(cfg.getRunCommand()).isEqualTo("python3 /code/solution.py");
            assertThat(cfg.getSourceCode()).isEqualTo(req.getSourceCode());
            assertThat(cfg.getStdinInput()).isEqualTo("stdin-data");
            assertThat(cfg.getTimeoutSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("never invokes ProblemServiceClient for RUN-type job")
        void runDoesNotFetchTestCases() {
            CodeExecutionRequestEvent req = runEvent(Language.PYTHON, null);
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.COMPLETED, "ok", "", 0, 10L));

            service.executeAsync(req);

            verify(problemServiceClient, never()).getTestCases(any(UUID.class));
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  executeAsync — SUBMIT-type (problemId != null)
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeAsync — SUBMIT-type")
    class SubmitType {

        @Test
        @DisplayName("ACCEPTED verdict when all test cases pass with matching trimmed stdout")
        void submitAllAccepted() {
            CodeExecutionRequestEvent req = submitEvent(Language.PYTHON);
            when(problemServiceClient.getTestCases(req.getProblemId()))
                    .thenReturn(List.of(tc("1", "2"), tc("3", "4")));
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.COMPLETED, "2\n", "", 0, 50L))
                    .thenReturn(execResult(ExecutionStatus.COMPLETED, "  4  ", "", 0, 60L));

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(eq(KafkaTopics.EXECUTION_RESULTS), anyString(), captor.capture());
            CodeExecutionResultEvent published = captor.getValue();
            assertThat(published.getVerdict()).isEqualTo(Verdict.ACCEPTED);
            assertThat(published.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(published.getExitCode()).isZero();
            assertThat(published.getProblemId()).isEqualTo(req.getProblemId());
            assertThat(published.getExecutionTimeMs()).isEqualTo(110L);

            verify(sandboxRunner, times(2)).run(any(ExecutionConfig.class));
        }

        @Test
        @DisplayName("WRONG_ANSWER and short-circuits on the first mismatching test case")
        void submitWrongAnswerShortCircuits() {
            CodeExecutionRequestEvent req = submitEvent(Language.PYTHON);
            when(problemServiceClient.getTestCases(req.getProblemId()))
                    .thenReturn(List.of(tc("1", "expected-A"), tc("2", "expected-B")));
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.COMPLETED, "wrong-A", "", 0, 10L));

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            CodeExecutionResultEvent published = captor.getValue();
            assertThat(published.getVerdict()).isEqualTo(Verdict.WRONG_ANSWER);
            assertThat(published.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(published.getExitCode()).isOne();
            assertThat(published.getStderr()).contains("Wrong Answer");

            // Must short-circuit — only first test case ran
            verify(sandboxRunner, times(1)).run(any(ExecutionConfig.class));
        }

        @Test
        @DisplayName("RUNTIME_ERROR and short-circuits when test-case run exits non-zero")
        void submitRuntimeError() {
            CodeExecutionRequestEvent req = submitEvent(Language.JAVA);
            when(problemServiceClient.getTestCases(req.getProblemId()))
                    .thenReturn(List.of(tc("1", "ok"), tc("2", "ok")));
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.FAILED, "", "NullPointerException", 1, 25L));

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            CodeExecutionResultEvent published = captor.getValue();
            assertThat(published.getVerdict()).isEqualTo(Verdict.RUNTIME_ERROR);
            assertThat(published.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(published.getStderr()).contains("NullPointerException");

            verify(sandboxRunner, times(1)).run(any(ExecutionConfig.class));
        }

        @Test
        @DisplayName("TIME_LIMIT_EXCEEDED when a test case executionTimeMs >= timeout*1000")
        void submitTle() {
            CodeExecutionRequestEvent req = submitEvent(Language.CPP);
            when(problemServiceClient.getTestCases(req.getProblemId()))
                    .thenReturn(List.of(tc("in", "out")));
            // timeoutSeconds = 10  =>  >=10_000 ms is TLE
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.TIMEDOUT, "", "", 0, 10_000L));

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().getVerdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
            assertThat(captor.getValue().getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(captor.getValue().getStderr()).contains("Time Limit Exceeded");
        }

        @Test
        @DisplayName("INTERNAL_ERROR when Problem Service returns empty test cases")
        void submitEmptyTestCasesYieldsInternalError() {
            CodeExecutionRequestEvent req = submitEvent(Language.PYTHON);
            when(problemServiceClient.getTestCases(req.getProblemId()))
                    .thenReturn(Collections.emptyList());

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
            assertThat(captor.getValue().getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(captor.getValue().getStderr()).contains("No test cases");
            verify(sandboxRunner, never()).run(any(ExecutionConfig.class));
        }

        @Test
        @DisplayName("INTERNAL_ERROR when Problem Service returns null list")
        void submitNullTestCasesYieldsInternalError() {
            CodeExecutionRequestEvent req = submitEvent(Language.PYTHON);
            when(problemServiceClient.getTestCases(req.getProblemId())).thenReturn(null);

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        }

        @Test
        @DisplayName("uses per-test-case stdin from TestCaseDto.input")
        void submitUsesTestCaseStdin() {
            CodeExecutionRequestEvent req = submitEvent(Language.PYTHON);
            TestCaseDto t1 = tc("input-1", "out-1");
            when(problemServiceClient.getTestCases(req.getProblemId())).thenReturn(List.of(t1));
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.COMPLETED, "out-1", "", 0, 10L));

            service.executeAsync(req);

            ArgumentCaptor<ExecutionConfig> captor = ArgumentCaptor.forClass(ExecutionConfig.class);
            verify(sandboxRunner).run(captor.capture());
            assertThat(captor.getValue().getStdinInput()).isEqualTo("input-1");
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Redis status updates
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Redis status updates")
    class RedisStatus {

        @Test
        @DisplayName("sets RUNNING before sandbox run, then final status after publish")
        void shouldSetRunningThenFinalStatus() {
            CodeExecutionRequestEvent req = runEvent(Language.PYTHON, null);
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.COMPLETED, "ok", "", 0, 10L));

            service.executeAsync(req);

            ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOps, atLeast(2))
                    .set(eq("job_status:" + req.getJobId()), statusCaptor.capture(), any(Duration.class));
            assertThat(statusCaptor.getAllValues())
                    .containsExactly("RUNNING", "COMPLETED");
        }

        @Test
        @DisplayName("sets FAILED in Redis when sandbox returns FAILED")
        void shouldUpdateRedisToFailed() {
            CodeExecutionRequestEvent req = runEvent(Language.PYTHON, null);
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.FAILED, "", "boom", 1, 5L));

            service.executeAsync(req);

            ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOps, atLeast(2))
                    .set(anyString(), statusCaptor.capture(), any(Duration.class));
            assertThat(statusCaptor.getAllValues()).contains("RUNNING", "FAILED");
        }

        @Test
        @DisplayName("uses 24-hour TTL on Redis key")
        void shouldUse24hTtl() {
            CodeExecutionRequestEvent req = runEvent(Language.PYTHON, null);
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenReturn(execResult(ExecutionStatus.COMPLETED, "ok", "", 0, 1L));

            service.executeAsync(req);

            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOps, atLeastOnce()).set(anyString(), anyString(), ttlCaptor.capture());
            assertThat(ttlCaptor.getAllValues()).allMatch(d -> d.equals(Duration.ofHours(24)));
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Exception handling
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeAsync — exception handling")
    class ExceptionPath {

        @Test
        @DisplayName("on uncaught exception, publishes INTERNAL_ERROR via publishFailedResult")
        void shouldPublishFailedResultOnException() {
            CodeExecutionRequestEvent req = runEvent(Language.PYTHON, null);
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenThrow(new RuntimeException("Docker daemon unreachable"));

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(eq(KafkaTopics.EXECUTION_RESULTS),
                    eq(req.getJobId().toString()), captor.capture());
            CodeExecutionResultEvent published = captor.getValue();
            assertThat(published.getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
            assertThat(published.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(published.getExitCode()).isEqualTo(-1);
            assertThat(published.getStderr()).contains("Docker daemon unreachable");
            assertThat(published.getUserId()).isEqualTo(req.getUserId());
        }

        @Test
        @DisplayName("on ProblemService failure (RuntimeException), publishes INTERNAL_ERROR")
        void shouldHandleProblemServiceFailure() {
            CodeExecutionRequestEvent req = submitEvent(Language.PYTHON);
            when(problemServiceClient.getTestCases(req.getProblemId()))
                    .thenThrow(new RuntimeException("Problem Service 503"));

            service.executeAsync(req);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
            assertThat(captor.getValue().getStderr()).contains("Problem Service 503");
        }

        @Test
        @DisplayName("does not propagate exception out of executeAsync (would break Kafka consumer)")
        void shouldSwallowException() {
            CodeExecutionRequestEvent req = runEvent(Language.PYTHON, null);
            when(sandboxRunner.run(any(ExecutionConfig.class)))
                    .thenThrow(new RuntimeException("boom"));

            // Must not throw
            service.executeAsync(req);
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  publishFailedResult overloads
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("publishFailedResult overloads")
    class PublishFailedResult {

        @Test
        @DisplayName("3-arg overload sets userId=null, problemId=null")
        void threeArgOverload() {
            UUID jobId = UUID.randomUUID();
            UUID subId = UUID.randomUUID();

            service.publishFailedResult(jobId, subId, "permanent failure");

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(eq(KafkaTopics.EXECUTION_RESULTS),
                    eq(jobId.toString()), captor.capture());

            CodeExecutionResultEvent ev = captor.getValue();
            assertThat(ev.getJobId()).isEqualTo(jobId);
            assertThat(ev.getSubmissionId()).isEqualTo(subId);
            assertThat(ev.getUserId()).isNull();
            assertThat(ev.getProblemId()).isNull();
            assertThat(ev.getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
            assertThat(ev.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(ev.getExitCode()).isEqualTo(-1);
            assertThat(ev.getStderr()).contains("permanent failure");
        }

        @Test
        @DisplayName("5-arg overload preserves userId and problemId")
        void fiveArgOverload() {
            UUID jobId = UUID.randomUUID();
            UUID subId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID problemId = UUID.randomUUID();

            service.publishFailedResult(jobId, subId, userId, problemId, "infra error");

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(eq(KafkaTopics.EXECUTION_RESULTS),
                    eq(jobId.toString()), captor.capture());

            CodeExecutionResultEvent ev = captor.getValue();
            assertThat(ev.getUserId()).isEqualTo(userId);
            assertThat(ev.getProblemId()).isEqualTo(problemId);
            assertThat(ev.getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
            assertThat(ev.getStderr()).isEqualTo("infra error");
        }

        @Test
        @DisplayName("also writes FAILED status to Redis with 24h TTL")
        void publishFailedAlsoUpdatesRedis() {
            UUID jobId = UUID.randomUUID();

            service.publishFailedResult(jobId, UUID.randomUUID(), "reason");

            verify(valueOps).set(eq("job_status:" + jobId),
                    eq("FAILED"), eq(Duration.ofHours(24)));
        }
    }
}