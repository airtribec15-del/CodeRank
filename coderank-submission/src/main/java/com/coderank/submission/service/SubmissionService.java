package com.coderank.submission.service;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.constants.RedisKeys;
import com.coderank.common.enums.ExecutionStatus;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private static final int TIMEOUT_SECONDS = 10;

    private final SubmissionRepository submissionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final SubmissionMapper submissionMapper;

    // ------------------------------------------------------------------ //
    //  RUN (ad-hoc – no problem id, custom stdin)                         //
    // ------------------------------------------------------------------ //

    @Transactional
    public SubmissionResponse run(RunRequest request, UUID userId) {
        UUID jobId = UUID.randomUUID();

        Submission submission = Submission.builder()
                .userId(userId)
                .jobId(jobId)
                .language(request.getLanguage())
                .submissionType(SubmissionType.RUN)
                .sourceCode(request.getSourceCode())
                .stdinInput(request.getStdinInput())
                .status(ExecutionStatus.QUEUED)
                .verdict(Verdict.PENDING)
                .build();

        submission = submissionRepository.save(submission);
        log.info("RUN submission created: {} (jobId={})", submission.getId(), jobId);

        publishExecutionRequest(submission, request.getStdinInput());
        cacheStatus(jobId, ExecutionStatus.QUEUED);

        return submissionMapper.toResponse(submission);
    }

    // ------------------------------------------------------------------ //
    //  SUBMIT (judge run against all problem test cases)                  //
    // ------------------------------------------------------------------ //

    @Transactional
    public SubmissionResponse submit(SubmitRequest request, UUID userId) {
        UUID jobId = UUID.randomUUID();

        Submission submission = Submission.builder()
                .userId(userId)
                .problemId(request.getProblemId())
                .jobId(jobId)
                .language(request.getLanguage())
                .submissionType(SubmissionType.SUBMIT)
                .sourceCode(request.getSourceCode())
                .status(ExecutionStatus.QUEUED)
                .verdict(Verdict.PENDING)
                .build();

        submission = submissionRepository.save(submission);
        log.info("SUBMIT submission created: {} (jobId={}, problemId={})",
                submission.getId(), jobId, request.getProblemId());

        // For SUBMIT, stdinInput is null – Execution Service fetches test cases
        // from the Problem Service internal endpoint.
        publishExecutionRequest(submission, null);
        cacheStatus(jobId, ExecutionStatus.QUEUED);

        return submissionMapper.toResponse(submission);
    }

    // ------------------------------------------------------------------ //
    //  Status polling                                                      //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getSubmission(UUID submissionId, UUID requestingUserId, boolean isAdmin) {
        Submission submission = findOrThrow(submissionId);
        if (!isAdmin && !submission.getUserId().equals(requestingUserId)) {
            throw new InvalidRequestException("Access denied to submission " + submissionId);
        }
        return submissionMapper.toDetailResponse(submission);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> getMySubmissions(UUID userId, Pageable pageable) {
        return submissionRepository.findAllByUserId(userId, pageable)
                .map(submissionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> getMySubmissionsForProblem(UUID userId, UUID problemId, Pageable pageable) {
        return submissionRepository.findAllByUserIdAndProblemId(userId, problemId, pageable)
                .map(submissionMapper::toResponse);
    }

    // ------------------------------------------------------------------ //
    //  Internal: result written back by Result Processor                  //
    // ------------------------------------------------------------------ //

    @Transactional
    public void updateSubmissionResult(UUID jobId,
                                       ExecutionStatus status,
                                       String stdout,
                                       String stderr,
                                       Integer exitCode,
                                       Long executionTimeMs,
                                       Verdict verdict) {
        Submission submission = submissionRepository.findByJobId(jobId)
                .orElseThrow(() -> new InvalidRequestException("No submission for jobId: " + jobId));

        submission.setStatus(status);
        submission.setStdout(stdout);
        submission.setStderr(stderr);
        submission.setExitCode(exitCode);
        submission.setExecutionTimeMs(executionTimeMs);
        submission.setVerdict(verdict);
        submission.setCompletedAt(Instant.now());

        submissionRepository.save(submission);
        cacheStatus(jobId, status);
        log.info("Submission {} updated: status={}, verdict={}", submission.getId(), status, verdict);
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private void publishExecutionRequest(Submission submission, String stdinInput) {
        CodeExecutionRequestEvent event = CodeExecutionRequestEvent.builder()
                .jobId(submission.getJobId())
                .submissionId(submission.getId())
                .userId(submission.getUserId())
                .language(submission.getLanguage())
                .sourceCode(submission.getSourceCode())
                .stdinInput(stdinInput)
                .timeoutSeconds(TIMEOUT_SECONDS)
                .submittedAt(Instant.now())
                .build();

        kafkaTemplate.send(KafkaTopics.EXECUTION_REQUESTS, submission.getJobId().toString(), event);
        log.debug("Published execution request event for jobId={}", submission.getJobId());
    }

    private void cacheStatus(UUID jobId, ExecutionStatus status) {
        String key = RedisKeys.jobStatusKey(jobId.toString());
        redisTemplate.opsForValue().set(key, status.name(), Duration.ofHours(2));
    }

    private Submission findOrThrow(UUID submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new InvalidRequestException("Submission not found: " + submissionId));
    }

    // ------------------------------------------------------------------ //
    //  DLT Fallback — called by DLT handler after all retries exhausted   //
    // ------------------------------------------------------------------ //

    /**
     * Marks a submission as permanently failed when the Kafka DLT handler
     * cannot recover the result event.  Prevents the submission staying
     * stuck in PENDING/QUEUED state forever.
     */
    @Transactional
    public void markSubmissionAsDltFailed(UUID jobId) {
        submissionRepository.findByJobId(jobId).ifPresentOrElse(
            submission -> {
                submission.setStatus(ExecutionStatus.FAILED);
                submission.setVerdict(Verdict.RUNTIME_ERROR);
                submission.setStderr("Result event permanently lost after all retries (DLT)");
                submission.setCompletedAt(Instant.now());
                submissionRepository.save(submission);
                cacheStatus(jobId, ExecutionStatus.FAILED);
                log.warn("Submission {} marked as DLT_FAILED for jobId={}", submission.getId(), jobId);
            },
            () -> log.error("markSubmissionAsDltFailed: no submission found for jobId={}", jobId)
        );
    }
}
