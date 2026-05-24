package com.coderank.submission.service;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.submission.dto.request.RunRequest;
import com.coderank.submission.dto.request.SubmitRequest;
import com.coderank.submission.dto.response.SubmissionDetailResponse;
import com.coderank.submission.dto.response.SubmissionResponse;
import com.coderank.submission.entity.Submission;
import com.coderank.submission.enums.SubmissionType;
import com.coderank.submission.enums.Verdict;
import com.coderank.submission.mapper.SubmissionMapper;
import com.coderank.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionService")
class SubmissionServiceTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SubmissionMapper submissionMapper;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private SubmissionService submissionService;

    private UUID userId;
    private UUID problemId;
    private UUID jobId;
    private Submission savedSubmission;
    private SubmissionResponse submissionResponse;
    private SubmissionDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        problemId = UUID.randomUUID();
        jobId     = UUID.randomUUID();

        savedSubmission = Submission.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .jobId(jobId)
                .language(Language.PYTHON)
                .submissionType(SubmissionType.RUN)
                .sourceCode("print('hello')")
                .stdinInput("world")
                .status(ExecutionStatus.QUEUED)
                .verdict(Verdict.PENDING)
                .createdAt(Instant.now())
                .build();

        submissionResponse = SubmissionResponse.builder()
                .submissionId(savedSubmission.getId())
                .jobId(jobId)
                .language(Language.PYTHON)
                .status(ExecutionStatus.QUEUED)
                .verdict(Verdict.PENDING)
                .createdAt(savedSubmission.getCreatedAt())
                .build();

        detailResponse = SubmissionDetailResponse.builder()
                .submissionId(savedSubmission.getId())
                .userId(userId)
                .jobId(jobId)
                .language(Language.PYTHON)
                .status(ExecutionStatus.QUEUED)
                .verdict(Verdict.PENDING)
                .createdAt(savedSubmission.getCreatedAt())
                .build();

        // Common stubs used across most tests
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doNothing().when(valueOps).set(anyString(), anyString(), any());
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
    }

    // ------------------------------------------------------------------ //
    //  run                                                                 //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("run")
    class Run {

        @Test
        @DisplayName("persists submission with RUN type and QUEUED status")
        void shouldPersistRunSubmission() {
            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON)
                    .sourceCode("print('hello')")
                    .stdinInput("world")
                    .build();

            when(submissionRepository.save(any(Submission.class))).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(savedSubmission)).thenReturn(submissionResponse);

            ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
            submissionService.run(request, userId);

            verify(submissionRepository).save(captor.capture());
            Submission saved = captor.getValue();
            assertThat(saved.getSubmissionType()).isEqualTo(SubmissionType.RUN);
            assertThat(saved.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
            assertThat(saved.getVerdict()).isEqualTo(Verdict.PENDING);
            assertThat(saved.getStdinInput()).isEqualTo("world");
            assertThat(saved.getProblemId()).isNull();
        }

        @Test
        @DisplayName("publishes a CodeExecutionRequestEvent to Kafka")
        void shouldPublishKafkaEvent() {
            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON).sourceCode("x=1").stdinInput("").build();

            when(submissionRepository.save(any())).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(savedSubmission)).thenReturn(submissionResponse);

            submissionService.run(request, userId);

            verify(kafkaTemplate).send(eq(KafkaTopics.EXECUTION_REQUESTS),
                    anyString(), any(CodeExecutionRequestEvent.class));
        }

        @Test
        @DisplayName("caches QUEUED status in Redis after save")
        void shouldCacheStatusInRedis() {
            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON).sourceCode("x=1").build();

            when(submissionRepository.save(any())).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(savedSubmission)).thenReturn(submissionResponse);

            submissionService.run(request, userId);

            verify(valueOps).set(anyString(), eq("QUEUED"), any());
        }

        @Test
        @DisplayName("returns mapped SubmissionResponse")
        void shouldReturnMappedResponse() {
            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON).sourceCode("x=1").build();

            when(submissionRepository.save(any())).thenReturn(savedSubmission);
            when(submissionMapper.toResponse(savedSubmission)).thenReturn(submissionResponse);

            SubmissionResponse result = submissionService.run(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        }
    }

    // ------------------------------------------------------------------ //
    //  submit                                                              //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("persists submission with SUBMIT type and the given problemId")
        void shouldPersistSubmitSubmission() {
            SubmitRequest request = SubmitRequest.builder()
                    .problemId(problemId)
                    .language(Language.JAVA)
                    .sourceCode("class S{}")
                    .build();

            Submission submitSubmission = savedSubmission.toBuilder()
                    .problemId(problemId)
                    .submissionType(SubmissionType.SUBMIT)
                    .language(Language.JAVA)
                    .stdinInput(null)
                    .build();

            when(submissionRepository.save(any())).thenReturn(submitSubmission);
            when(submissionMapper.toResponse(submitSubmission)).thenReturn(submissionResponse);

            ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
            submissionService.submit(request, userId);

            verify(submissionRepository).save(captor.capture());
            assertThat(captor.getValue().getSubmissionType()).isEqualTo(SubmissionType.SUBMIT);
            assertThat(captor.getValue().getProblemId()).isEqualTo(problemId);
            assertThat(captor.getValue().getStdinInput()).isNull();
        }

        @Test
        @DisplayName("publishes Kafka event with null stdinInput for SUBMIT")
        void shouldPublishKafkaEventWithNullStdin() {
            SubmitRequest request = SubmitRequest.builder()
                    .problemId(problemId).language(Language.JAVA).sourceCode("class S{}").build();

            Submission submitSub = savedSubmission.toBuilder()
                    .submissionType(SubmissionType.SUBMIT).stdinInput(null).build();

            when(submissionRepository.save(any())).thenReturn(submitSub);
            when(submissionMapper.toResponse(submitSub)).thenReturn(submissionResponse);

            submissionService.submit(request, userId);

            ArgumentCaptor<CodeExecutionRequestEvent> eventCaptor =
                    ArgumentCaptor.forClass(CodeExecutionRequestEvent.class);
            verify(kafkaTemplate).send(anyString(), anyString(), eventCaptor.capture());
            assertThat(eventCaptor.getValue().getStdinInput()).isNull();
        }
    }

    // ------------------------------------------------------------------ //
    //  getSubmission                                                       //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("getSubmission")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class GetSubmission {

        @Test
        @DisplayName("returns detail response for the submission owner")
        void shouldReturnDetailForOwner() {
            when(submissionRepository.findById(savedSubmission.getId()))
                    .thenReturn(Optional.of(savedSubmission));
            when(submissionMapper.toDetailResponse(savedSubmission)).thenReturn(detailResponse);

            SubmissionDetailResponse result =
                    submissionService.getSubmission(savedSubmission.getId(), userId, false);

            assertThat(result.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("admin can access any submission")
        void shouldAllowAdminToAccessAnySubmission() {
            UUID adminId = UUID.randomUUID();
            when(submissionRepository.findById(savedSubmission.getId()))
                    .thenReturn(Optional.of(savedSubmission));
            when(submissionMapper.toDetailResponse(savedSubmission)).thenReturn(detailResponse);

            assertThatNoException().isThrownBy(() ->
                    submissionService.getSubmission(savedSubmission.getId(), adminId, true));
        }

        @Test
        @DisplayName("throws InvalidRequestException when a non-owner non-admin requests")
        void shouldThrowForUnauthorizedUser() {
            UUID otherId = UUID.randomUUID();
            when(submissionRepository.findById(savedSubmission.getId()))
                    .thenReturn(Optional.of(savedSubmission));

            assertThatThrownBy(() ->
                    submissionService.getSubmission(savedSubmission.getId(), otherId, false))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Access denied");
        }

        @Test
        @DisplayName("throws InvalidRequestException when submission not found")
        void shouldThrowWhenNotFound() {
            UUID missing = UUID.randomUUID();
            when(submissionRepository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    submissionService.getSubmission(missing, userId, false))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Submission not found");
        }
    }

    // ------------------------------------------------------------------ //
    //  getMySubmissions                                                    //
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

    // ------------------------------------------------------------------ //
    //  getMySubmissionsForProblem                                          //
    // ------------------------------------------------------------------ //
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
    }

    // ------------------------------------------------------------------ //
    //  updateSubmissionResult                                              //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("updateSubmissionResult")
    class UpdateSubmissionResult {

        @Test
        @DisplayName("updates all result fields and saves")
        void shouldUpdateAllResultFields() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.of(savedSubmission));
            when(submissionRepository.save(savedSubmission)).thenReturn(savedSubmission);

            submissionService.updateSubmissionResult(
                    jobId, ExecutionStatus.COMPLETED, "[0,1]", "", 0, 120L, Verdict.ACCEPTED);

            assertThat(savedSubmission.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(savedSubmission.getStdout()).isEqualTo("[0,1]");
            assertThat(savedSubmission.getExitCode()).isEqualTo(0);
            assertThat(savedSubmission.getExecutionTimeMs()).isEqualTo(120L);
            assertThat(savedSubmission.getVerdict()).isEqualTo(Verdict.ACCEPTED);
            assertThat(savedSubmission.getCompletedAt()).isNotNull();
            verify(submissionRepository).save(savedSubmission);
        }

        @Test
        @DisplayName("caches updated status in Redis")
        void shouldCacheUpdatedStatus() {
            when(submissionRepository.findByJobId(jobId)).thenReturn(Optional.of(savedSubmission));
            when(submissionRepository.save(any())).thenReturn(savedSubmission);

            submissionService.updateSubmissionResult(
                    jobId, ExecutionStatus.FAILED, null, "err", 1, 50L, Verdict.RUNTIME_ERROR);

            verify(valueOps).set(anyString(), eq("FAILED"), any());
        }

        @Test
        @DisplayName("throws InvalidRequestException when jobId not found")
        void shouldThrowWhenJobIdNotFound() {
            UUID unknownJobId = UUID.randomUUID();
            when(submissionRepository.findByJobId(unknownJobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    submissionService.updateSubmissionResult(
                            unknownJobId, ExecutionStatus.COMPLETED, null, null, 0, 0L, Verdict.ACCEPTED))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("No submission for jobId");
        }
    }
}
