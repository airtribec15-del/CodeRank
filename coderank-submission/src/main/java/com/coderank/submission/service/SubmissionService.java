package com.coderank.submission.service;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.constants.RedisKeys;
import com.coderank.common.enums.ExecutionStatus;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${submission.default-timeout-seconds:10}")
    private int defaultTimeoutSeconds;

    private final SubmissionRepository submissionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final SubmissionMapper submissionMapper;

    // ── RUN — ad-hoc, no problem id, custom stdin ───────────────────────────

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

        // FIX: saveAndFlush forces an immediate SQL INSERT + flush within the
        // transaction, causing Hibernate to populate @CreationTimestamp on the
        // returned entity before toResponse() reads it. Plain save() defers the
        // flush to transaction commit, leaving createdAt null in the response.
        submission = submissionRepository.saveAndFlush(submission);
        log.info("RUN submission created id={} jobId={}", submission.getId(), jobId);

        publishExecutionRequest(submission, request.getStdinInput());
        cacheStatus(jobId, ExecutionStatus.QUEUED, null);  // QUEUED has no verdict yet

        return submissionMapper.toResponse(submission);
    }

    // ── SUBMIT — judge run against all problem test cases ──────────────────

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

        // FIX: saveAndFlush forces an immediate SQL INSERT + flush within the
        // transaction, causing Hibernate to populate @CreationTimestamp on the
        // returned entity before toResponse() reads it. Plain save() defers the
        // flush to transaction commit, leaving createdAt null in the response.
        submission = submissionRepository.saveAndFlush(submission);
        log.info("SUBMIT submission created id={} jobId={} problemId={}",
                submission.getId(), jobId, request.getProblemId());

        publishExecutionRequest(submission, null);
        cacheStatus(jobId, ExecutionStatus.QUEUED, null);  // QUEUED has no verdict yet

        return submissionMapper.toResponse(submission);
    }

    // ── Status polling ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getSubmission(UUID submissionId, UUID requestingUserId, boolean isAdmin) {
        Submission submission = findOrThrow(submissionId);
        if (!isAdmin && !submission.getUserId().equals(requestingUserId)) {
            throw new InvalidRequestException("Access denied to submission " + submissionId);
        }
        return submissionMapper.toDetailResponse(submission);
    }

    /**
     * Redis-cache-first result polling (GAP-03 FIX).
     *
     * <p><strong>Locked Flow Step 8:</strong> Client polls
     * {@code GET /api/v1/submissions/{id}/result} repeatedly after receiving
     * 202 Accepted. This method checks Redis first (sub-millisecond) for the
     * job status and verdict. Only on cache miss does it fall back to a DB read.</p>
     *
     * <p><strong>Cache key:</strong> {@code job-status:{jobId}}<br>
     * <strong>Cache value format:</strong> {@code "STATUS"} (while in-flight) or
     * {@code "STATUS:VERDICT"} (after Result Processor completes, e.g.
     * {@code "COMPLETED:ACCEPTED"}, {@code "COMPLETED:WRONG_ANSWER"},
     * {@code "TIMEDOUT"}).</p>
     *
     * <p>Both Submission Service's {@link #cacheStatus} and Result Processor's
     * {@code cacheJobResult()} write to this same key with the same compound format,
     * ensuring consistent reads regardless of which service last wrote.</p>
     *
     * @param submissionId     the submission UUID (used to find the jobId for the cache key)
     * @param requestingUserId the authenticated user (ownership check)
     * @param isAdmin          admins can view any submission
     */
    @Transactional(readOnly = true)
    public JobResultResponse getJobResult(UUID submissionId, UUID requestingUserId, boolean isAdmin) {
        // Ownership check requires the submission record; this is a single indexed PK lookup
        Submission submission = findOrThrow(submissionId);
        if (!isAdmin && !submission.getUserId().equals(requestingUserId)) {
            throw new InvalidRequestException("Access denied to submission " + submissionId);
        }

        String cacheKey = RedisKeys.jobStatusKey(submission.getJobId().toString());
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);

        if (cachedValue != null) {
            log.debug("Cache HIT for submissionId={} jobId={} value={}",
                    submissionId, submission.getJobId(), cachedValue);
            return parseJobResultFromCache(submission.getJobId(), submissionId, cachedValue);
        }

        // Cache miss: fall back to DB (happens if Redis evicted the key or service restarted)
        log.debug("Cache MISS for submissionId={} jobId={} — falling back to DB",
                submissionId, submission.getJobId());
        return JobResultResponse.builder()
                .jobId(submission.getJobId())
                .submissionId(submissionId)
                .status(submission.getStatus())
                .verdict(submission.getVerdict())
                .executionTimeMs(submission.getExecutionTimeMs())
                .completedAt(submission.getCompletedAt())
                .source("db")
                .build();
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

    // ── Internal result update — called via InternalSubmissionController ───

    @Transactional
    public void updateSubmissionResult(UUID jobId, ExecutionStatus status,
                                       String stdout, String stderr,
                                       Integer exitCode, Long executionTimeMs,
                                       Verdict verdict) {
        Submission submission = submissionRepository.findByJobId(jobId)
                .orElseThrow(() -> new InvalidRequestException("No submission for jobId " + jobId));

        submission.setStatus(status);
        submission.setStdout(stdout);
        submission.setStderr(stderr);
        submission.setExitCode(exitCode);
        submission.setExecutionTimeMs(executionTimeMs);
        submission.setVerdict(verdict);
        submission.setCompletedAt(Instant.now());

        submissionRepository.save(submission);

        // FIX-A: Write compound "STATUS:VERDICT" format to align with
        // Result Processor's cacheJobResult() — both writers now use the same format.
        // This prevents the DB-write path from overwriting the Result Processor's
        // verdict-enriched cache value with a plain status string.
        cacheStatus(jobId, status, verdict);

        log.info("Submission updated id={} status={} verdict={}", submission.getId(), status, verdict);
    }

    // ── DLT Fallback ────────────────────────────────────────────────────────

    @Transactional
    public void markSubmissionAsDltFailed(UUID jobId) {
        submissionRepository.findByJobId(jobId).ifPresentOrElse(
                submission -> {
                    submission.setStatus(ExecutionStatus.FAILED);
                    submission.setVerdict(Verdict.INTERNAL_ERROR);
                    submission.setStderr("Result event permanently lost after all retries (DLT)");
                    submission.setCompletedAt(Instant.now());
                    submissionRepository.save(submission);
                    cacheStatus(jobId, ExecutionStatus.FAILED, Verdict.INTERNAL_ERROR);
                    log.warn("Submission marked as DLT-FAILED id={} jobId={}", submission.getId(), jobId);
                },
                () -> log.error("markSubmissionAsDltFailed: no submission found for jobId={}", jobId)
        );
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void publishExecutionRequest(Submission submission, String stdinInput) {
        CodeExecutionRequestEvent event = CodeExecutionRequestEvent.builder()
                .jobId(submission.getJobId())
                .submissionId(submission.getId())
                .userId(submission.getUserId())
                .problemId(submission.getProblemId())
                .language(submission.getLanguage())
                .sourceCode(submission.getSourceCode())
                .stdinInput(stdinInput)
                .timeoutSeconds(defaultTimeoutSeconds)
                .submittedAt(Instant.now())
                .build();

        kafkaTemplate.send(KafkaTopics.EXECUTION_REQUESTS, submission.getJobId().toString(), event);
        log.debug("Published execution request event for jobId={}", submission.getJobId());
    }

    /**
     * Caches job status in Redis using the compound {@code "STATUS:VERDICT"} format.
     *
     * <p>This format is shared between Submission Service (initial QUEUED/RUNNING writes)
     * and Result Processor (terminal COMPLETED/FAILED/TIMEDOUT writes).
     * All readers ({@link #getJobResult}) parse this compound string consistently.</p>
     *
     * @param jobId   the Kafka correlation ID for this job
     * @param status  the {@link ExecutionStatus} to cache
     * @param verdict the {@link Verdict} — null for in-flight states (QUEUED, RUNNING)
     */
    private void cacheStatus(UUID jobId, ExecutionStatus status, Verdict verdict) {
        String key = RedisKeys.jobStatusKey(jobId.toString());
        // Compound format: "COMPLETED:ACCEPTED", "TIMEDOUT", "QUEUED", "RUNNING"
        String value = status.name() + (verdict != null ? ":" + verdict.name() : "");
        redisTemplate.opsForValue().set(key, value, Duration.ofHours(24));
    }

    /**
     * Parses the compound Redis cache value {@code "STATUS[:VERDICT]"} into a
     * {@link JobResultResponse}.
     *
     * <p>Examples: {@code "QUEUED"}, {@code "RUNNING"}, {@code "COMPLETED:ACCEPTED"},
     * {@code "COMPLETED:WRONG_ANSWER"}, {@code "TIMEDOUT"}, {@code "FAILED:INTERNAL_ERROR"}.</p>
     */
    private JobResultResponse parseJobResultFromCache(UUID jobId, UUID submissionId, String cachedValue) {
        String[] parts = cachedValue.split(":", 2);
        ExecutionStatus status;
        Verdict verdict = null;

        try {
            status = ExecutionStatus.valueOf(parts[0]);
        } catch (IllegalArgumentException ex) {
            log.warn("Unrecognised status in Redis cache '{}' for jobId={}", cachedValue, jobId);
            status = ExecutionStatus.FAILED;
        }

        if (parts.length == 2) {
            try {
                verdict = Verdict.valueOf(parts[1]);
            } catch (IllegalArgumentException ex) {
                log.warn("Unrecognised verdict in Redis cache '{}' for jobId={}", cachedValue, jobId);
            }
        }

        return JobResultResponse.builder()
                .jobId(jobId)
                .submissionId(submissionId)
                .status(status)
                .verdict(verdict)
                .source("cache")
                .build();
    }

    private Submission findOrThrow(UUID submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new InvalidRequestException("Submission not found " + submissionId));
    }
}