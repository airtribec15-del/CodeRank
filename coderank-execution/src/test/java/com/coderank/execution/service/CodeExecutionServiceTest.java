package com.coderank.execution.service;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.execution.docker.DockerSandboxRunner;
import com.coderank.execution.model.ExecutionResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeExecutionService")
class CodeExecutionServiceTest {

    @Mock private DockerSandboxRunner sandboxRunner;
    @Mock private LanguageConfigResolver languageConfigResolver;
    @Mock private KafkaTemplate<String, CodeExecutionResultEvent> resultKafkaTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private CodeExecutionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "timeoutSeconds", 10);
        ReflectionTestUtils.setField(service, "memoryLimitMb", 128);
        ReflectionTestUtils.setField(service, "cpuPeriod", 100000L);
        ReflectionTestUtils.setField(service, "cpuQuota", 50000L);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doNothing().when(valueOps).set(anyString(), anyString(), any());

        when(languageConfigResolver.resolve(any())).thenReturn(
                new LanguageConfigResolver.LanguageProfile(
                        "python:3.11-slim", "solution.py", "python3 /code/solution.py"));
    }

    private CodeExecutionRequestEvent buildRequest(Language language, String stdin) {
        return CodeExecutionRequestEvent.builder()
                .jobId(UUID.randomUUID())
                .submissionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .language(language)
                .sourceCode("print('hello')")
                .stdinInput(stdin)
                .timeoutSeconds(10)
                .submittedAt(Instant.now())
                .build();
    }

    private ExecutionResult completedResult() {
        return ExecutionResult.builder()
                .status(ExecutionStatus.COMPLETED)
                .stdout("[0,1]")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(120L)
                .completedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("executeAsync – happy path")
    class HappyPath {

        @Test
        @DisplayName("sets RUNNING in Redis before Docker run")
        void shouldSetRunningStatusBeforeDockerRun() {
            CodeExecutionRequestEvent request = buildRequest(Language.PYTHON, "5");
            when(sandboxRunner.run(any())).thenReturn(completedResult());

            service.executeAsync(request);

            // First Redis call → RUNNING, second → final status (COMPLETED)
            ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOps, atLeast(2)).set(anyString(), statusCaptor.capture(), any());
            assertThat(statusCaptor.getAllValues()).contains("RUNNING", "COMPLETED");
        }

        @Test
        @DisplayName("publishes CodeExecutionResultEvent to execution.results topic")
        void shouldPublishResultEvent() {
            CodeExecutionRequestEvent request = buildRequest(Language.PYTHON, null);
            ExecutionResult result = completedResult();
            when(sandboxRunner.run(any())).thenReturn(result);

            service.executeAsync(request);

            ArgumentCaptor<CodeExecutionResultEvent> eventCaptor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(eq("code.execution.results"),
                    eq(request.getJobId().toString()), eventCaptor.capture());

            CodeExecutionResultEvent published = eventCaptor.getValue();
            assertThat(published.getJobId()).isEqualTo(request.getJobId());
            assertThat(published.getSubmissionId()).isEqualTo(request.getSubmissionId());
            assertThat(published.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(published.getStdout()).isEqualTo("[0,1]");
        }

        @Test
        @DisplayName("uses request timeout when > 0, falls back to configured default")
        void shouldUseRequestTimeoutWhenPositive() {
            CodeExecutionRequestEvent request = buildRequest(Language.JAVA, null);
            when(sandboxRunner.run(any())).thenReturn(completedResult());

            service.executeAsync(request);

            ArgumentCaptor<com.coderank.execution.model.ExecutionConfig> configCaptor =
                    ArgumentCaptor.forClass(com.coderank.execution.model.ExecutionConfig.class);
            verify(sandboxRunner).run(configCaptor.capture());
            assertThat(configCaptor.getValue().getTimeoutSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("uses configured default timeout when request timeout is 0")
        void shouldFallbackToDefaultTimeout() {
            CodeExecutionRequestEvent request = CodeExecutionRequestEvent.builder()
                    .jobId(UUID.randomUUID()).submissionId(UUID.randomUUID())
                    .userId(UUID.randomUUID()).language(Language.PYTHON)
                    .sourceCode("print(1)").timeoutSeconds(0).submittedAt(Instant.now())
                    .build();
            when(sandboxRunner.run(any())).thenReturn(completedResult());

            service.executeAsync(request);

            ArgumentCaptor<com.coderank.execution.model.ExecutionConfig> configCaptor =
                    ArgumentCaptor.forClass(com.coderank.execution.model.ExecutionConfig.class);
            verify(sandboxRunner).run(configCaptor.capture());
            assertThat(configCaptor.getValue().getTimeoutSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("publishes TIMED_OUT result when Docker runner times out")
        void shouldPublishTimedOutResult() {
            CodeExecutionRequestEvent request = buildRequest(Language.CPP, null);
            ExecutionResult timeout = ExecutionResult.builder()
                    .status(ExecutionStatus.TIMED_OUT).stdout("").stderr("Timed out")
                    .exitCode(1).executionTimeMs(10000L).completedAt(Instant.now()).build();
            when(sandboxRunner.run(any())).thenReturn(timeout);

            service.executeAsync(request);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ExecutionStatus.TIMED_OUT);
        }

        @Test
        @DisplayName("publishes FAILED result when process exits non-zero")
        void shouldPublishFailedResult() {
            CodeExecutionRequestEvent request = buildRequest(Language.JAVASCRIPT, "");
            ExecutionResult failed = ExecutionResult.builder()
                    .status(ExecutionStatus.FAILED).stdout("").stderr("ReferenceError")
                    .exitCode(1).executionTimeMs(50L).completedAt(Instant.now()).build();
            when(sandboxRunner.run(any())).thenReturn(failed);

            service.executeAsync(request);

            ArgumentCaptor<CodeExecutionResultEvent> captor =
                    ArgumentCaptor.forClass(CodeExecutionResultEvent.class);
            verify(resultKafkaTemplate).send(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(captor.getValue().getStderr()).contains("ReferenceError");
        }

        @Test
        @DisplayName("maps memory limit correctly from MB to bytes")
        void shouldMapMemoryLimitToBytes() {
            CodeExecutionRequestEvent request = buildRequest(Language.PYTHON, null);
            when(sandboxRunner.run(any())).thenReturn(completedResult());

            service.executeAsync(request);

            ArgumentCaptor<com.coderank.execution.model.ExecutionConfig> captor =
                    ArgumentCaptor.forClass(com.coderank.execution.model.ExecutionConfig.class);
            verify(sandboxRunner).run(captor.capture());
            assertThat(captor.getValue().getMemoryLimitBytes())
                    .isEqualTo(128L * 1024 * 1024);
        }
    }

    @Nested
    @DisplayName("executeAsync – Redis updates")
    class RedisUpdates {

        @Test
        @DisplayName("updates Redis to COMPLETED after successful execution")
        void shouldUpdateRedisToCompleted() {
            CodeExecutionRequestEvent request = buildRequest(Language.PYTHON, null);
            when(sandboxRunner.run(any())).thenReturn(completedResult());

            service.executeAsync(request);

            verify(valueOps, atLeast(2)).set(
                    eq("job_status:" + request.getJobId()), anyString(), any());
        }

        @Test
        @DisplayName("updates Redis even when execution fails")
        void shouldUpdateRedisOnFailure() {
            CodeExecutionRequestEvent request = buildRequest(Language.PYTHON, null);
            ExecutionResult failed = ExecutionResult.builder()
                    .status(ExecutionStatus.FAILED).stdout("").stderr("error")
                    .exitCode(1).executionTimeMs(100L).completedAt(Instant.now()).build();
            when(sandboxRunner.run(any())).thenReturn(failed);

            service.executeAsync(request);

            ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
            verify(valueOps, atLeast(2)).set(anyString(), statusCaptor.capture(), any());
            assertThat(statusCaptor.getAllValues()).contains("FAILED");
        }
    }
}
