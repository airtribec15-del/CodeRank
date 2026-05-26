// src/test/java/com/coderank/submission/service/SubmissionServiceTest.java
package com.coderank.submission.service;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.constants.RedisKeys;
import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.submission.dto.request.RunRequest;
import com.coderank.submission.dto.request.SubmitRequest;
import com.coderank.submission.dto.response.JobResultResponse;
import com.coderank.submission.dto.response.SubmissionDetailResponse;
import com.coderank.submission.dto.response.SubmissionResponse;
import com.coderank.submission.entity.Submission;
import com.coderank.submission.enums.SubmissionType;
import com.coderank.submission.mapper.SubmissionMapper;
import com.coderank.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionService")
class SubmissionServiceTest {

    @Mock SubmissionRepository submissionRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SubmissionMapper submissionMapper;

    @InjectMocks SubmissionService submissionService;

    private UUID userId;
    private UUID problemId;
    private UUID jobId;
    private UUID submissionId;
    private Submission savedSubmission;
    private SubmissionResponse submissionResponse;
    private SubmissionDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(submissionService, "defaultTimeoutSeconds", 10);

        userId = UUID.randomUUID();
        problemId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        submissionId = UUID.randomUUID();

        savedSubmission = Submission.builder()
                .id(submissionId)
                .userId(userId)
                .problemId(problemId)
                .jobId(jobId)
                .language(Language.PYTHON)
                .submissionType(SubmissionType.SUBMIT)
                .sourceCode("print(1)")
                .status(ExecutionStatus.QUEUED)
                .verdict(Verdict.PENDING)
                .createdAt(Instant.now())
                .build();

        submissionResponse = SubmissionResponse.builder()
                .submissionId(submissionId)
                .jobId(jobId)
                .language(Language.PYTHON)
                .submissionType(SubmissionType.RUN)
                .status(ExecutionStatus.QUEUED)
                .verdict(Verdict.PENDING)
                .createdAt(Instant.now())
                .build();

        detailResponse = SubmissionDetailResponse.builder()
                .submissionId(submissionId)
                .userId(userId)
                .jobId(jobId)
                .language(Language.PYTHON)
                .submissionType(SubmissionType.SUBMIT)
                .status(ExecutionStatus.COMPLETED)
                .verdict(Verdict.ACCEPTED)
                .sourceCode("print(1)")
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ------------------------------------------------------------------ //
    //  run()                                                             //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("run")
    class Run {

        @Test
        @DisplayName("persists a RUN submission with correct fields")
        void shouldPersistRunSubmission() {
            when(submissionRepository.save(any(Submission.class))).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(savedSubmission)).thenReturn(submissionResponse);

            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON)
                    .sourceCode("print(1)")
                    .stdinInput("5")
                    .build();

            SubmissionResponse result = submissionService.run(request, userId);

            ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
            verify(submissionRepository).save(captor.capture());
            Submission persisted = captor.getValue();

            assertThat(persisted.getUserId()).isEqualTo(userId);
            assertThat(persisted.getSubmissionType()).isEqualTo(SubmissionType.RUN);
            assertThat(persisted.getProblemId()).isNull();
            assertThat(persisted.getLanguage()).isEqualTo(Language.PYTHON);
            assertThat(persisted.getSourceCode()).isEqualTo("print(1)");
            assertThat(persisted.getStdinInput()).isEqualTo("5");
            assertThat(persisted.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
            assertThat(persisted.getVerdict()).isEqualTo(Verdict.PENDING);
            assertThat(persisted.getJobId()).isNotNull();
            assertThat(result).isEqualTo(submissionResponse);
        }

        @Test
        @DisplayName("publishes execution request to correct topic with jobId as key")
        void shouldPublishToKafka() {
            when(submissionRepository.save(any())).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(any())).thenReturn(submissionResponse);

            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON)
                    .sourceCode("x=1")
                    .stdinInput("input")
                    .build();

            submissionService.run(request, userId);

            verify(kafkaTemplate).send(
                    eq(KafkaTopics.EXECUTION_REQUESTS),
                    eq(jobId.toString()),
                    argThat(evt -> {
                        var e = (CodeExecutionRequestEvent) evt;
                        return e.getJobId().equals(jobId)
                                && e.getUserId().equals(userId)
                                && e.getProblemId() != null // saved entity has problemId
                                && e.getLanguage() == Language.PYTHON
                                && "input".equals(e.getStdinInput())
                                && e.getTimeoutSeconds() == 10
                                && e.getSubmittedAt() != null;
                    })
            );
        }

        @Test
        @DisplayName("publishes event with null stdinInput when RunRequest has no stdin")
        void shouldPublishWithNullStdin() {
            // For RUN with no stdin, the persisted submission should also have null stdin
            Submission noStdin = Submission.builder()
                    .id(submissionId).userId(userId).jobId(jobId)
                    .language(Language.PYTHON).submissionType(SubmissionType.RUN)
                    .sourceCode("x=1").status(ExecutionStatus.QUEUED).verdict(Verdict.PENDING)
                    .build();
            when(submissionRepository.save(any())).thenReturn(noStdin);
            when(submissionMapper.toResponse(any())).thenReturn(submissionResponse);

            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON).sourceCode("x=1").build();

            submissionService.run(request, userId);

            verify(kafkaTemplate).send(
                    eq(KafkaTopics.EXECUTION_REQUESTS),
                    eq(jobId.toString()),
                    argThat(evt -> ((CodeExecutionRequestEvent) evt).getStdinInput() == null)
            );
        }

        @Test
        @DisplayName("caches QUEUED status in Redis after run (key prefix only — jobId is generated inside service)")
        void shouldCacheQueuedStatusAfterRun() {
            when(submissionRepository.save(any())).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(any())).thenReturn(submissionResponse);

            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON)
                    .sourceCode("x=1")
                    .build();

            submissionService.run(request, userId);

            // The service generates a fresh UUID internally for jobId — we can only assert
            // the key prefix, the value, and that a Duration was provided.
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

            verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

            assertThat(keyCaptor.getValue()).startsWith("job_status:");
            // Tail of the key must be a valid UUID
            String uuidPart = keyCaptor.getValue().substring("job_status:".length());
            assertThatCode(() -> UUID.fromString(uuidPart)).doesNotThrowAnyException();
            assertThat(valueCaptor.getValue()).isEqualTo("QUEUED");
            assertThat(ttlCaptor.getValue()).isNotNull();
            assertThat(ttlCaptor.getValue().isPositive()).isTrue();
        }
    }

    // ------------------------------------------------------------------ //
    //  submit()                                                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("persists a SUBMIT submission with problemId and no stdinInput")
        void shouldPersistSubmitSubmission() {
            when(submissionRepository.save(any(Submission.class))).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(savedSubmission)).thenReturn(submissionResponse);

            SubmitRequest request = SubmitRequest.builder()
                    .problemId(problemId)
                    .language(Language.JAVA)
                    .sourceCode("class S{}")
                    .build();

            submissionService.submit(request, userId);

            ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
            verify(submissionRepository).save(captor.capture());
            Submission persisted = captor.getValue();

            assertThat(persisted.getProblemId()).isEqualTo(problemId);
            assertThat(persisted.getSubmissionType()).isEqualTo(SubmissionType.SUBMIT);
            assertThat(persisted.getStdinInput()).isNull();
            assertThat(persisted.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
            assertThat(persisted.getLanguage()).isEqualTo(Language.JAVA);
        }

        @Test
        @DisplayName("publishes event with problemId and null stdinInput")
        void shouldPublishWithProblemId() {
            when(submissionRepository.save(any())).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(any())).thenReturn(submissionResponse);

            SubmitRequest request = SubmitRequest.builder()
                    .problemId(problemId)
                    .language(Language.JAVA)
                    .sourceCode("class S{}")
                    .build();

            submissionService.submit(request, userId);

            verify(kafkaTemplate).send(
                    eq(KafkaTopics.EXECUTION_REQUESTS),
                    eq(jobId.toString()),
                    argThat(evt -> {
                        var e = (CodeExecutionRequestEvent) evt;
                        return problemId.equals(e.getProblemId())
                                && e.getStdinInput() == null
                                && e.getLanguage() == Language.PYTHON; // savedSubmission language
                    })
            );
        }

        @Test
        @DisplayName("caches QUEUED status in Redis after submit (key prefix only — jobId is generated inside service)")
        void shouldCacheQueuedStatusAfterSubmit() {
            when(submissionRepository.save(any())).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(any())).thenReturn(submissionResponse);

            SubmitRequest request = SubmitRequest.builder()
                    .problemId(problemId)
                    .language(Language.JAVA)
                    .sourceCode("class S {}")
                    .build();

            submissionService.submit(request, userId);

            // The service generates a fresh UUID internally for jobId — assert only the
            // key prefix, the value, and that a positive Duration TTL was provided.
            ArgumentCaptor<String>   keyCaptor   = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String>   valueCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Duration> ttlCaptor   = ArgumentCaptor.forClass(Duration.class);

            verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

            assertThat(keyCaptor.getValue()).startsWith("job_status:");
            String uuidPart = keyCaptor.getValue().substring("job_status:".length());
            assertThatCode(() -> UUID.fromString(uuidPart)).doesNotThrowAnyException();
            assertThat(valueCaptor.getValue()).isEqualTo("QUEUED");
            assertThat(ttlCaptor.getValue()).isNotNull();
            assertThat(ttlCaptor.getValue().isPositive()).isTrue();
        }
    }

    // ------------------------------------------------------------------ //
    //  getSubmission()                                                   //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getSubmission")
    class GetSubmission {

        @Test
        @DisplayName("returns detail response for the submission owner")
        void shouldReturnDetailForOwner() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));
            when(submissionMapper.toDetailResponse(savedSubmission)).thenReturn(detailResponse);

            SubmissionDetailResponse result =
                    submissionService.getSubmission(submissionId, userId, false);

            assertThat(result).isEqualTo(detailResponse);
        }

        @Test
        @DisplayName("admin can access any user's submission")
        void shouldAllowAdminToAccessAnySubmission() {
            UUID adminId = UUID.randomUUID();
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));
            when(submissionMapper.toDetailResponse(savedSubmission)).thenReturn(detailResponse);

            assertThatNoException().isThrownBy(() ->
                    submissionService.getSubmission(submissionId, adminId, true));

            verify(submissionMapper).toDetailResponse(savedSubmission);
        }

        @Test
        @DisplayName("throws InvalidRequestException when a non-owner non-admin requests")
        void shouldThrowForUnauthorizedUser() {
            UUID otherId = UUID.randomUUID();
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));

            assertThatThrownBy(() -> submissionService.getSubmission(submissionId, otherId, false))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Access denied");

            verify(submissionMapper, never()).toDetailResponse(any());
        }

        @Test
        @DisplayName("throws InvalidRequestException when submission not found")
        void shouldThrowWhenNotFound() {
            UUID missing = UUID.randomUUID();
            when(submissionRepository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> submissionService.getSubmission(missing, userId, false))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Submission not found");
        }
    }

    // ------------------------------------------------------------------ //
    //  getJobResult()                                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getJobResult")
    class GetJobResult {

        @Test
        @DisplayName("returns cache-hit response with source=cache when Redis has the key")
        void shouldReturnCacheHitResponse() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));
            String cacheKey = RedisKeys.jobStatusKey(jobId.toString());
            when(valueOps.get(cacheKey)).thenReturn("COMPLETED:ACCEPTED");

            JobResultResponse result = submissionService.getJobResult(submissionId, userId, false);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(result.getVerdict()).isEqualTo(Verdict.ACCEPTED);
            assertThat(result.getSource()).isEqualTo("cache");
            assertThat(result.getJobId()).isEqualTo(jobId);
            assertThat(result.getSubmissionId()).isEqualTo(submissionId);
            assertThat(result.getCompletedAt()).isNull(); // not stored in cache
        }

        @Test
        @DisplayName("returns cache response for in-flight QUEUED status (no verdict)")
        void shouldReturnQueuedFromCache() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));
            when(valueOps.get(any())).thenReturn("QUEUED");

            JobResultResponse result = submissionService.getJobResult(submissionId, userId, false);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
            assertThat(result.getVerdict()).isNull();
            assertThat(result.getSource()).isEqualTo("cache");
        }

        @Test
        @DisplayName("returns DB fallback response with source=db on cache miss")
        void shouldFallbackToDbOnCacheMiss() {
            Submission completedSubmission = Submission.builder()
                    .id(submissionId)
                    .userId(userId)
                    .problemId(problemId)
                    .jobId(jobId)
                    .language(Language.PYTHON)
                    .submissionType(SubmissionType.SUBMIT)
                    .sourceCode("print(1)")
                    .status(ExecutionStatus.COMPLETED)
                    .verdict(Verdict.ACCEPTED)
                    .executionTimeMs(150L)
                    .completedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(completedSubmission));
            when(valueOps.get(any())).thenReturn(null);

            JobResultResponse result = submissionService.getJobResult(submissionId, userId, false);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(result.getVerdict()).isEqualTo(Verdict.ACCEPTED);
            assertThat(result.getSource()).isEqualTo("db");
            assertThat(result.getExecutionTimeMs()).isEqualTo(150L);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("parses FAILED:INTERNAL_ERROR from cache correctly")
        void shouldParseFailedInternalError() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));
            when(valueOps.get(any())).thenReturn("FAILED:INTERNAL_ERROR");

            JobResultResponse result = submissionService.getJobResult(submissionId, userId, false);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(result.getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
            assertThat(result.getSource()).isEqualTo("cache");
        }

        @Test
        @DisplayName("parses TIMEDOUT:TIME_LIMIT_EXCEEDED from cache correctly")
        void shouldParseTimedOutTle() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));
            when(valueOps.get(any())).thenReturn("TIMEDOUT:TIME_LIMIT_EXCEEDED");

            JobResultResponse result = submissionService.getJobResult(submissionId, userId, false);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.TIMEDOUT);
            assertThat(result.getVerdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("defaults to FAILED status for unrecognised status part in cache")
        void shouldDefaultToFailedForUnrecognisedStatus() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));
            when(valueOps.get(any())).thenReturn("GARBAGE_VALUE");

            JobResultResponse result = submissionService.getJobResult(submissionId, userId, false);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(result.getVerdict()).isNull();
            assertThat(result.getSource()).isEqualTo("cache");
        }

        @Test
        @DisplayName("ignores unrecognised verdict part but keeps recognised status")
        void shouldIgnoreUnrecognisedVerdict() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));
            when(valueOps.get(any())).thenReturn("COMPLETED:NOT_A_REAL_VERDICT");

            JobResultResponse result = submissionService.getJobResult(submissionId, userId, false);

            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(result.getVerdict()).isNull();
        }

        @Test
        @DisplayName("admin can poll any user's job result")
        void shouldAllowAdminToPollAnyResult() {
            UUID adminId = UUID.randomUUID();
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));
            when(valueOps.get(any())).thenReturn("QUEUED");

            assertThatNoException().isThrownBy(() ->
                    submissionService.getJobResult(submissionId, adminId, true));
        }

        @Test
        @DisplayName("throws InvalidRequestException for non-owner non-admin")
        void shouldThrowForUnauthorizedUser() {
            UUID otherId = UUID.randomUUID();
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(savedSubmission));

            assertThatThrownBy(() -> submissionService.getJobResult(submissionId, otherId, false))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Access denied");
        }

        @Test
        @DisplayName("throws InvalidRequestException when submission record not found")
        void shouldThrowWhenSubmissionMissing() {
            UUID missing = UUID.randomUUID();
            when(submissionRepository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> submissionService.getJobResult(missing, userId, false))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Submission not found");
        }
    }

    // ------------------------------------------------------------------ //
    //  getMySubmissions / getMySubmissionsForProblem                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getMySubmissions")
    class GetMySubmissions {

        @Test
        @DisplayName("returns page of user submissions")
        void shouldReturnPagedSubmissions() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Submission> page = new PageImpl<>(List.of(savedSubmission), pageable, 1);

            when(submissionRepository.findAllByUserId(userId, pageable)).thenReturn(page);
            when(submissionMapper.toResponse(savedSubmission)).thenReturn(submissionResponse);

            Page<SubmissionResponse> result = submissionService.getMySubmissions(userId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        }

        @Test
        @DisplayName("returns empty page when user has no submissions")
        void shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 20);
            when(submissionRepository.findAllByUserId(userId, pageable))
                    .thenReturn(Page.empty(pageable));

            Page<SubmissionResponse> result = submissionService.getMySubmissions(userId, pageable);
            assertThat(result.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("getMySubmissionsForProblem")
    class GetMySubmissionsForProblem {

        @Test
        @DisplayName("filters submissions by userId and problemId")
        void shouldFilterByProblem() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Submission> page = new PageImpl<>(List.of(savedSubmission), pageable, 1);

            when(submissionRepository.findAllByUserIdAndProblemId(userId, problemId, pageable))
                    .thenReturn(page);
            when(submissionMapper.toResponse(savedSubmission)).thenReturn(submissionResponse);

            Page<SubmissionResponse> result =
                    submissionService.getMySubmissionsForProblem(userId, problemId, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(submissionRepository).findAllByUserIdAndProblemId(userId, problemId, pageable);
        }

        @Test
        @DisplayName("returns empty page when user has no submissions for that problem")
        void shouldReturnEmptyWhenNoneForProblem() {
            Pageable pageable = PageRequest.of(0, 20);
            when(submissionRepository.findAllByUserIdAndProblemId(userId, problemId, pageable))
                    .thenReturn(Page.empty(pageable));

            Page<SubmissionResponse> result =
                    submissionService.getMySubmissionsForProblem(userId, problemId, pageable);

            assertThat(result.isEmpty()).isTrue();
        }
    }

    // ------------------------------------------------------------------ //
    //  updateSubmissionResult()                                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("updateSubmissionResult")
    class UpdateSubmissionResult {

        @Test
        @DisplayName("updates all result fields and saves to DB")
        void shouldUpdateAllResultFields() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.of(savedSubmission));
            when(submissionRepository.save(savedSubmission)).thenReturn(savedSubmission);

            submissionService.updateSubmissionResult(
                    jobId, ExecutionStatus.COMPLETED, "[0,1]", "", 0, 120L, Verdict.ACCEPTED);

            assertThat(savedSubmission.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(savedSubmission.getStdout()).isEqualTo("[0,1]");
            assertThat(savedSubmission.getStderr()).isEqualTo("");
            assertThat(savedSubmission.getExitCode()).isEqualTo(0);
            assertThat(savedSubmission.getExecutionTimeMs()).isEqualTo(120L);
            assertThat(savedSubmission.getVerdict()).isEqualTo(Verdict.ACCEPTED);
            assertThat(savedSubmission.getCompletedAt()).isNotNull();
            verify(submissionRepository).save(savedSubmission);
        }

        @Test
        @DisplayName("caches compound STATUS:VERDICT format in Redis on COMPLETED + ACCEPTED")
        void shouldCacheCompoundStatusVerdictOnCompleted() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.of(savedSubmission));
            when(submissionRepository.save(any())).thenReturn(savedSubmission);

            submissionService.updateSubmissionResult(
                    jobId, ExecutionStatus.COMPLETED, "out", "", 0, 120L, Verdict.ACCEPTED);

            String expectedKey = RedisKeys.jobStatusKey(jobId.toString());
            verify(valueOps).set(eq(expectedKey), eq("COMPLETED:ACCEPTED"), eq(Duration.ofHours(24)));
        }

        @Test
        @DisplayName("caches FAILED:RUNTIME_ERROR in Redis on FAILED + RUNTIME_ERROR")
        void shouldCacheFailedRuntimeError() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.of(savedSubmission));
            when(submissionRepository.save(any())).thenReturn(savedSubmission);

            submissionService.updateSubmissionResult(
                    jobId, ExecutionStatus.FAILED, null, "err", 1, 50L, Verdict.RUNTIME_ERROR);

            String expectedKey = RedisKeys.jobStatusKey(jobId.toString());
            verify(valueOps).set(eq(expectedKey), eq("FAILED:RUNTIME_ERROR"), any(Duration.class));
        }

        @Test
        @DisplayName("caches compound TIMEDOUT:TIME_LIMIT_EXCEEDED in Redis")
        void shouldCacheTimedOutVerdict() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.of(savedSubmission));
            when(submissionRepository.save(any())).thenReturn(savedSubmission);

            submissionService.updateSubmissionResult(
                    jobId, ExecutionStatus.TIMEDOUT, "", "TLE", 1, 5000L,
                    Verdict.TIME_LIMIT_EXCEEDED);

            verify(valueOps).set(anyString(), eq("TIMEDOUT:TIME_LIMIT_EXCEEDED"), any(Duration.class));
        }

        @Test
        @DisplayName("caches plain STATUS (no colon suffix) when verdict is null")
        void shouldCachePlainStatusWhenVerdictNull() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.of(savedSubmission));
            when(submissionRepository.save(any())).thenReturn(savedSubmission);

            submissionService.updateSubmissionResult(
                    jobId, ExecutionStatus.RUNNING, null, null, null, null, null);

            verify(valueOps).set(anyString(), eq("RUNNING"), any(Duration.class));
        }

        @Test
        @DisplayName("throws InvalidRequestException when jobId not found")
        void shouldThrowWhenJobIdNotFound() {
            UUID unknownJobId = UUID.randomUUID();
            when(submissionRepository.findByJobId(unknownJobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    submissionService.updateSubmissionResult(
                            unknownJobId, ExecutionStatus.COMPLETED,
                            null, null, 0, 0L, Verdict.ACCEPTED))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("No submission for jobId");

            verify(submissionRepository, never()).save(any());
            verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        }
    }

    // ------------------------------------------------------------------ //
    //  markSubmissionAsDltFailed()                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("markSubmissionAsDltFailed")
    class MarkSubmissionAsDltFailed {

        @Test
        @DisplayName("sets FAILED status and INTERNAL_ERROR verdict and saves")
        void shouldMarkSubmissionAsFailed() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.of(savedSubmission));
            when(submissionRepository.save(any())).thenReturn(savedSubmission);

            submissionService.markSubmissionAsDltFailed(jobId);

            assertThat(savedSubmission.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(savedSubmission.getVerdict()).isEqualTo(Verdict.INTERNAL_ERROR);
            assertThat(savedSubmission.getStderr()).contains("DLT");
            assertThat(savedSubmission.getCompletedAt()).isNotNull();
            verify(submissionRepository).save(savedSubmission);
        }

        @Test
        @DisplayName("caches FAILED:INTERNAL_ERROR in Redis")
        void shouldCacheFailedInternalErrorInRedis() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.of(savedSubmission));
            when(submissionRepository.save(any())).thenReturn(savedSubmission);

            submissionService.markSubmissionAsDltFailed(jobId);

            verify(valueOps).set(anyString(), eq("FAILED:INTERNAL_ERROR"), any(Duration.class));
        }

        @Test
        @DisplayName("does NOT throw when jobId is not found (best-effort, logs only)")
        void shouldNotThrowWhenJobIdMissing() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() ->
                    submissionService.markSubmissionAsDltFailed(jobId));

            verify(submissionRepository, never()).save(any());
            verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        }
    }
}