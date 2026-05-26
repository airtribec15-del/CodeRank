package com.coderank.resultprocessor.service;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.constants.RedisKeys;
import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.common.event.StateUpdateEvent;
import com.coderank.resultprocessor.client.SubmissionServiceClient;
import com.coderank.resultprocessor.dto.UpdateSubmissionResultRequest;
import com.coderank.resultprocessor.exception.NonRetryableResultException;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResultProcessorService — pipeline orchestration tests")
class ResultProcessorServiceTest {

    @Mock
    private SubmissionServiceClient submissionServiceClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private KafkaTemplate<String, StateUpdateEvent> stateUpdateKafkaTemplate;

    @InjectMocks
    private ResultProcessorService service;

    private UUID jobId;
    private UUID submissionId;
    private UUID userId;
    private UUID problemId;
    private Instant completedAt;

    /**
     * Pre-built successful Kafka send future. Built EAGERLY in @BeforeEach so the
     * inner stub on SendResult.getRecordMetadata() does NOT live inside an outer
     * `when(stateUpdateKafkaTemplate.send(...))` call — preventing Mockito's
     * UnfinishedStubbingException.
     */
    private CompletableFuture<SendResult<String, StateUpdateEvent>> kafkaSuccessFuture;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "jobResultTtlHours", 24);
        jobId        = UUID.randomUUID();
        submissionId = UUID.randomUUID();
        userId       = UUID.randomUUID();
        problemId    = UUID.randomUUID();
        completedAt  = Instant.parse("2026-01-01T10:00:00Z");

        // Build the success future OUTSIDE any other stubbing context.
        @SuppressWarnings("unchecked")
        SendResult<String, StateUpdateEvent> sendResult = mock(SendResult.class);
        RecordMetadata rm = new RecordMetadata(
                new TopicPartition(KafkaTopics.STATE_UPDATE_EVENTS, 1),
                10L, 0, 0L, 0, 0);
        // lenient() because some tests (RUN-type) never read getRecordMetadata()
        lenient().when(sendResult.getRecordMetadata()).thenReturn(rm);
        kafkaSuccessFuture = CompletableFuture.completedFuture(sendResult);
    }

    /* ---------- helpers ---------- */

    private CodeExecutionResultEvent submitEvent() {
        return CodeExecutionResultEvent.builder()
                .jobId(jobId)
                .submissionId(submissionId)
                .userId(userId)
                .problemId(problemId)
                .verdict(Verdict.ACCEPTED)
                .status(ExecutionStatus.COMPLETED)
                .stdout("ok")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(123L)
                .completedAt(completedAt)
                .build();
    }

    private CodeExecutionResultEvent runEvent() {
        return CodeExecutionResultEvent.builder()
                .jobId(jobId)
                .submissionId(submissionId)
                .userId(userId)
                .problemId(null)        // RUN-type
                .verdict(null)          // no verdict for RUN
                .status(ExecutionStatus.COMPLETED)
                .stdout("hello")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(50L)
                .completedAt(completedAt)
                .build();
    }

    /* ---------- Step 1 — Validation ---------- */

    @Nested
    @DisplayName("Step 1 — Validation")
    class ValidationTests {

        @Test
        @DisplayName("null jobId throws NonRetryableResultException and never touches downstream")
        void nullJobIdThrows() {
            CodeExecutionResultEvent event = submitEvent();
            event.setJobId(null);

            assertThatThrownBy(() -> service.process(event))
                    .isInstanceOf(NonRetryableResultException.class)
                    .hasMessageContaining("null jobId");

            verifyNoInteractions(submissionServiceClient, redisTemplate, stateUpdateKafkaTemplate);
        }

        @Test
        @DisplayName("null submissionId throws NonRetryableResultException with jobId in message")
        void nullSubmissionIdThrows() {
            CodeExecutionResultEvent event = submitEvent();
            event.setSubmissionId(null);

            assertThatThrownBy(() -> service.process(event))
                    .isInstanceOf(NonRetryableResultException.class)
                    .hasMessageContaining("null submissionId")
                    .hasMessageContaining(jobId.toString());

            verifyNoInteractions(submissionServiceClient, redisTemplate, stateUpdateKafkaTemplate);
        }
    }

    /* ---------- Happy path — SUBMIT type ---------- */

    @Nested
    @DisplayName("Happy path — SUBMIT-type job")
    class SubmitHappyPath {

        @Test
        @DisplayName("Executes all 4 steps in order and builds correct UpdateSubmissionResultRequest")
        void submitHappyPath() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(stateUpdateKafkaTemplate.send(anyString(), anyString(), any(StateUpdateEvent.class)))
                    .thenReturn(kafkaSuccessFuture);

            CodeExecutionResultEvent event = submitEvent();
            service.process(event);

            // Step 2 — Submission Service update payload
            ArgumentCaptor<UpdateSubmissionResultRequest> reqCaptor =
                    ArgumentCaptor.forClass(UpdateSubmissionResultRequest.class);
            verify(submissionServiceClient).updateSubmissionResult(reqCaptor.capture());
            UpdateSubmissionResultRequest req = reqCaptor.getValue();
            assertThat(req.getJobId()).isEqualTo(jobId);
            assertThat(req.getSubmissionId()).isEqualTo(submissionId);
            assertThat(req.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(req.getVerdict()).isEqualTo(Verdict.ACCEPTED);
            assertThat(req.getStdout()).isEqualTo("ok");
            assertThat(req.getStderr()).isEqualTo("");
            assertThat(req.getExitCode()).isEqualTo(0);
            assertThat(req.getExecutionTimeMs()).isEqualTo(123L);
            assertThat(req.getCompletedAt()).isEqualTo(completedAt);

            // Step 3 — Redis cache
            verify(valueOperations).set(
                    eq(RedisKeys.jobStatusKey(jobId.toString())),
                    eq("COMPLETED:ACCEPTED"),
                    eq(Duration.ofHours(24)));

            // Step 4 — StateUpdateEvent published with userId as key
            ArgumentCaptor<StateUpdateEvent> stateCaptor = ArgumentCaptor.forClass(StateUpdateEvent.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(stateUpdateKafkaTemplate).send(
                    eq(KafkaTopics.STATE_UPDATE_EVENTS),
                    keyCaptor.capture(),
                    stateCaptor.capture());
            assertThat(keyCaptor.getValue()).isEqualTo(userId.toString());
            StateUpdateEvent published = stateCaptor.getValue();
            assertThat(published.getJobId()).isEqualTo(jobId);
            assertThat(published.getSubmissionId()).isEqualTo(submissionId);
            assertThat(published.getUserId()).isEqualTo(userId);
            assertThat(published.getProblemId()).isEqualTo(problemId);
            assertThat(published.getVerdict()).isEqualTo(Verdict.ACCEPTED);
            assertThat(published.getCompletedAt()).isEqualTo(completedAt);

            // Execution ORDER: submission update → redis → kafka send
            InOrder inOrder = inOrder(submissionServiceClient, valueOperations, stateUpdateKafkaTemplate);
            inOrder.verify(submissionServiceClient).updateSubmissionResult(any());
            inOrder.verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
            inOrder.verify(stateUpdateKafkaTemplate).send(anyString(), anyString(), any(StateUpdateEvent.class));
        }

        @Test
        @DisplayName("Falls back to submissionId as Kafka key when userId is null")
        void fallbackKafkaKeyWhenUserIdNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(stateUpdateKafkaTemplate.send(anyString(), anyString(), any(StateUpdateEvent.class)))
                    .thenReturn(kafkaSuccessFuture);

            CodeExecutionResultEvent event = submitEvent();
            event.setUserId(null);

            service.process(event);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(stateUpdateKafkaTemplate).send(
                    eq(KafkaTopics.STATE_UPDATE_EVENTS),
                    keyCaptor.capture(),
                    any(StateUpdateEvent.class));
            assertThat(keyCaptor.getValue()).isEqualTo(submissionId.toString());
        }

        @Test
        @DisplayName("Falls back to Instant.now() in UpdateRequest and StateUpdateEvent when completedAt is null")
        void fallbackCompletedAtWhenNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(stateUpdateKafkaTemplate.send(anyString(), anyString(), any(StateUpdateEvent.class)))
                    .thenReturn(kafkaSuccessFuture);

            CodeExecutionResultEvent event = submitEvent();
            event.setCompletedAt(null);

            Instant before = Instant.now();
            service.process(event);
            Instant after = Instant.now();

            ArgumentCaptor<UpdateSubmissionResultRequest> reqCaptor =
                    ArgumentCaptor.forClass(UpdateSubmissionResultRequest.class);
            verify(submissionServiceClient).updateSubmissionResult(reqCaptor.capture());
            assertThat(reqCaptor.getValue().getCompletedAt())
                    .isBetween(before.minusSeconds(1), after.plusSeconds(1));

            ArgumentCaptor<StateUpdateEvent> stateCaptor = ArgumentCaptor.forClass(StateUpdateEvent.class);
            verify(stateUpdateKafkaTemplate).send(anyString(), anyString(), stateCaptor.capture());
            assertThat(stateCaptor.getValue().getCompletedAt())
                    .isBetween(before.minusSeconds(1), after.plusSeconds(1));
        }

        @Test
        @DisplayName("Honors custom TTL injected via @Value")
        void customTtlHonored() {
            ReflectionTestUtils.setField(service, "jobResultTtlHours", 6);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(stateUpdateKafkaTemplate.send(anyString(), anyString(), any(StateUpdateEvent.class)))
                    .thenReturn(kafkaSuccessFuture);

            service.process(submitEvent());

            verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofHours(6)));
        }
    }

    /* ---------- Happy path — RUN type ---------- */

    @Nested
    @DisplayName("Happy path — RUN-type job")
    class RunHappyPath {

        @Test
        @DisplayName("Skips state-update publish when problemId is null, still updates Submission + Redis")
        void runJobSkipsStateUpdate() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            CodeExecutionResultEvent event = runEvent();
            service.process(event);

            verify(submissionServiceClient).updateSubmissionResult(any());
            verify(valueOperations).set(
                    eq(RedisKeys.jobStatusKey(jobId.toString())),
                    eq("COMPLETED"),                // no verdict suffix
                    eq(Duration.ofHours(24)));
            verifyNoInteractions(stateUpdateKafkaTemplate);
        }

        @Test
        @DisplayName("UpdateSubmissionResultRequest.verdict is null for RUN-type jobs")
        void runJobVerdictIsNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            service.process(runEvent());

            ArgumentCaptor<UpdateSubmissionResultRequest> reqCaptor =
                    ArgumentCaptor.forClass(UpdateSubmissionResultRequest.class);
            verify(submissionServiceClient).updateSubmissionResult(reqCaptor.capture());
            assertThat(reqCaptor.getValue().getVerdict()).isNull();
        }
    }

    /* ---------- Redis value formatting ---------- */

    @Nested
    @DisplayName("Redis cache value formatting")
    class RedisCacheValueTests {

        @Test
        @DisplayName("FAILED status with RUNTIME_ERROR verdict produces 'FAILED:RUNTIME_ERROR'")
        void failedWithRuntimeError() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(stateUpdateKafkaTemplate.send(anyString(), anyString(), any(StateUpdateEvent.class)))
                    .thenReturn(kafkaSuccessFuture);

            CodeExecutionResultEvent event = submitEvent();
            event.setStatus(ExecutionStatus.FAILED);
            event.setVerdict(Verdict.RUNTIME_ERROR);

            service.process(event);

            verify(valueOperations).set(anyString(), eq("FAILED:RUNTIME_ERROR"), any(Duration.class));
        }

        @Test
        @DisplayName("TIMEDOUT with TIME_LIMIT_EXCEEDED produces 'TIMEDOUT:TIME_LIMIT_EXCEEDED'")
        void timedoutWithTle() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(stateUpdateKafkaTemplate.send(anyString(), anyString(), any(StateUpdateEvent.class)))
                    .thenReturn(kafkaSuccessFuture);

            CodeExecutionResultEvent event = submitEvent();
            event.setStatus(ExecutionStatus.TIMEDOUT);
            event.setVerdict(Verdict.TIME_LIMIT_EXCEEDED);

            service.process(event);

            verify(valueOperations).set(anyString(), eq("TIMEDOUT:TIME_LIMIT_EXCEEDED"), any(Duration.class));
        }
    }

    /* ---------- Failure propagation ---------- */

    @Nested
    @DisplayName("Failure propagation — pipeline aborts on any step failure")
    class FailurePropagation {

        @Test
        @DisplayName("Submission Service failure aborts before Redis and Kafka")
        void submissionFailureAborts() {
            doThrow(new RuntimeException("submission svc down"))
                    .when(submissionServiceClient).updateSubmissionResult(any());

            assertThatThrownBy(() -> service.process(submitEvent()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("submission svc down");

            verifyNoInteractions(redisTemplate, stateUpdateKafkaTemplate);
        }

        @Test
        @DisplayName("NonRetryableResultException from Submission Service propagates as-is")
        void nonRetryableFromSubmission() {
            doThrow(new NonRetryableResultException("4xx rejected"))
                    .when(submissionServiceClient).updateSubmissionResult(any());

            assertThatThrownBy(() -> service.process(submitEvent()))
                    .isInstanceOf(NonRetryableResultException.class)
                    .hasMessageContaining("4xx rejected");

            verifyNoInteractions(redisTemplate, stateUpdateKafkaTemplate);
        }

        @Test
        @DisplayName("Redis failure aborts before Kafka publish")
        void redisFailureAborts() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            doThrow(new RuntimeException("redis down"))
                    .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            assertThatThrownBy(() -> service.process(submitEvent()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("redis down");

            verify(submissionServiceClient).updateSubmissionResult(any());
            verifyNoInteractions(stateUpdateKafkaTemplate);
        }

        @Test
        @DisplayName("Kafka send ExecutionException is wrapped as RuntimeException with jobId in message")
        @SuppressWarnings("unchecked")
        void kafkaExecutionExceptionWrapped() throws Exception {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // Mock the CompletableFuture directly so .get() throws ExecutionException
            // (this is what production catches as `Exception ex` and re-wraps).
            CompletableFuture<SendResult<String, StateUpdateEvent>> failingFuture =
                    mock(CompletableFuture.class);
            when(failingFuture.get()).thenThrow(
                    new ExecutionException("broker down", new RuntimeException("io")));
            when(stateUpdateKafkaTemplate.send(anyString(), anyString(), any(StateUpdateEvent.class)))
                    .thenReturn(failingFuture);

            assertThatThrownBy(() -> service.process(submitEvent()))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(NonRetryableResultException.class)
                    .hasMessageContaining("Failed to publish StateUpdateEvent")
                    .hasMessageContaining(jobId.toString());

            verify(submissionServiceClient).updateSubmissionResult(any());
            verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("Kafka send InterruptedException sets interrupt flag and wraps as RuntimeException")
        @SuppressWarnings("unchecked")
        void kafkaInterruptedHandled() throws Exception {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            CompletableFuture<SendResult<String, StateUpdateEvent>> future = mock(CompletableFuture.class);
            when(future.get()).thenThrow(new InterruptedException("interrupted"));
            when(stateUpdateKafkaTemplate.send(anyString(), anyString(), any(StateUpdateEvent.class)))
                    .thenReturn(future);

            // Ensure interrupt flag starts clean
            Thread.interrupted();

            try {
                assertThatThrownBy(() -> service.process(submitEvent()))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("Interrupted while publishing StateUpdateEvent")
                        .hasMessageContaining(jobId.toString());

                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                // Always clear interrupt flag so the test runner isn't affected
                Thread.interrupted();
            }
        }
    }
}