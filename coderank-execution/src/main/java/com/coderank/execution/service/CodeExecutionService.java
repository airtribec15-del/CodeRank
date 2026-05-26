package com.coderank.execution.service;

import com.coderank.common.constants.KafkaTopics;
import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.execution.client.ProblemServiceClient;
import com.coderank.execution.docker.DockerSandboxRunner;
import com.coderank.execution.model.ExecutionConfig;
import com.coderank.execution.model.ExecutionResult;
import com.coderank.execution.model.TestCaseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core execution orchestrator.
 *
 * <p>Two job types:
 * <ul>
 *   <li><b>RUN</b>    — execute against a single user-provided stdin (problemId == null);
 *                       verdict is null, status is the sandbox status.</li>
 *   <li><b>SUBMIT</b> — execute against all test cases fetched from Problem Service;
 *                       verdict is computed from per-test comparison.</li>
 * </ul>
 *
 * <p>Runs on the {@code executionTaskExecutor} pool (see AsyncConfig) so the Kafka
 * consumer thread returns immediately after dispatching.
 *
 * <p>Redis is updated to {@code RUNNING} before the sandbox call, then to the final
 * status after the result event is published. TTL = 24 hours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionService {

    private static final String  JOB_STATUS_KEY_PREFIX = "job_status:";
    private static final Duration JOB_STATUS_TTL       = Duration.ofHours(24);

    private final DockerSandboxRunner sandboxRunner;
    private final LanguageConfigResolver languageConfigResolver;
    private final KafkaTemplate<String, CodeExecutionResultEvent> resultKafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ProblemServiceClient problemServiceClient;

    // ════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ════════════════════════════════════════════════════════════════════

    @Async("executionTaskExecutor")
    public void executeAsync(CodeExecutionRequestEvent request) {
        // Safety net: jobId must never be null here — consume() guards this on
        // the Kafka listener thread. If somehow bypassed (e.g. direct service
        // call in a test), drop it immediately rather than NPE mid-execution.
        if (request.getJobId() == null) {
            log.error("[exec-task] Dropping request with null jobId — should have been rejected by consumer");
            return;
        }

        log.info("[exec-task] EXECUTE START jobId={} submissionId={} problemId={} lang={}",
                request.getJobId(), request.getSubmissionId(),
                request.getProblemId(), request.getLanguage());

        try {
            setRedisStatus(request.getJobId(), "RUNNING");

            CodeExecutionResultEvent result = (request.getProblemId() == null)
                    ? executeRun(request)
                    : executeSubmit(request);

            resultKafkaTemplate.send(
                    KafkaTopics.EXECUTION_RESULTS,
                    request.getJobId().toString(),
                    result);

            setRedisStatus(request.getJobId(), result.getStatus().name());

            log.info("[exec-task] EXECUTE DONE jobId={} status={} verdict={}",
                    request.getJobId(), result.getStatus(), result.getVerdict());

        } catch (Throwable ex) {
            log.error("[exec-task] EXECUTE FAILED jobId={}: {}",
                    request.getJobId(), ex.getMessage(), ex);
            String reason = ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
            publishFailedResult(
                    request.getJobId(),
                    request.getSubmissionId(),
                    request.getUserId(),
                    request.getProblemId(),
                    reason);
        }
    }

    /**
     * 3-arg overload — called by the DLT handler when the originating event
     * may not be fully trusted; userId/problemId set to null.
     */
    public void publishFailedResult(UUID jobId, UUID submissionId, String reason) {
        publishFailedResult(jobId, submissionId, null, null, reason);
    }

    /**
     * 5-arg overload — full failure context propagated to Result Processor
     * so downstream submission state updates can target the right user/problem.
     */
    public void publishFailedResult(UUID jobId,
                                    UUID submissionId,
                                    UUID userId,
                                    UUID problemId,
                                    String reason) {
        CodeExecutionResultEvent failure = CodeExecutionResultEvent.builder()
                .jobId(jobId)
                .submissionId(submissionId)
                .userId(userId)
                .problemId(problemId)
                .verdict(Verdict.INTERNAL_ERROR)
                .status(ExecutionStatus.FAILED)
                .stdout("")
                .stderr(reason == null ? "Unknown internal error" : reason)
                .exitCode(-1)
                .executionTimeMs(0L)
                .completedAt(Instant.now())
                .build();

        resultKafkaTemplate.send(
                KafkaTopics.EXECUTION_RESULTS,
                jobId.toString(),
                failure);

        setRedisStatus(jobId, "FAILED");

        log.warn("[exec-task] FAILED RESULT PUBLISHED jobId={} reason={}", jobId, reason);
    }

    // ════════════════════════════════════════════════════════════════════
    //  RUN  (custom stdin, no test cases)
    // ════════════════════════════════════════════════════════════════════

    private CodeExecutionResultEvent executeRun(CodeExecutionRequestEvent request) {
        ExecutionConfig cfg = buildExecutionConfig(
                request,
                request.getStdinInput() == null ? "" : request.getStdinInput());

        ExecutionResult sr = sandboxRunner.run(cfg);

        return CodeExecutionResultEvent.builder()
                .jobId(request.getJobId())
                .submissionId(request.getSubmissionId())
                .userId(request.getUserId())
                .problemId(null)
                .verdict(null) // RUN-type: verdict intentionally null
                .status(sr.getStatus())
                .stdout(sr.getStdout())
                .stderr(sr.getStderr())
                .exitCode(sr.getExitCode())
                .executionTimeMs(sr.getExecutionTimeMs())
                .completedAt(Instant.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════════
    //  SUBMIT  (fetch test cases, run each, compute verdict)
    // ════════════════════════════════════════════════════════════════════

    private CodeExecutionResultEvent executeSubmit(CodeExecutionRequestEvent request) {
        List<TestCaseDto> testCases = problemServiceClient.getTestCases(
                request.getProblemId(),
                request.getUserId());

        if (testCases == null || testCases.isEmpty()) {
            log.warn("No test cases found for problemId={}", request.getProblemId());
            return CodeExecutionResultEvent.builder()
                    .jobId(request.getJobId())
                    .submissionId(request.getSubmissionId())
                    .userId(request.getUserId())
                    .problemId(request.getProblemId())
                    .verdict(Verdict.INTERNAL_ERROR)
                    .status(ExecutionStatus.FAILED)
                    .stdout("")
                    .stderr("No test cases configured for problem " + request.getProblemId())
                    .exitCode(-1)
                    .executionTimeMs(0L)
                    .completedAt(Instant.now())
                    .build();
        }

        long totalTimeMs = 0L;
        long timeoutMs   = request.getTimeoutSeconds() * 1000L;

        for (TestCaseDto tc : testCases) {
            ExecutionConfig cfg = buildExecutionConfig(
                    request,
                    tc.getInput() == null ? "" : tc.getInput());

            ExecutionResult sr = sandboxRunner.run(cfg);
            totalTimeMs += sr.getExecutionTimeMs() == null ? 0L : sr.getExecutionTimeMs();

            // ── 1. TIME LIMIT EXCEEDED ────────────────────────────────
            if (sr.getStatus() == ExecutionStatus.TIMEDOUT
                    || (sr.getExecutionTimeMs() != null && sr.getExecutionTimeMs() >= timeoutMs)) {
                return buildFailedSubmitResult(
                        request, totalTimeMs,
                        Verdict.TIME_LIMIT_EXCEEDED,
                        "Time Limit Exceeded on test case " + tc.getId(),
                        sr.getStdout());
            }

            // ── 2. RUNTIME ERROR ──────────────────────────────────────
            if (sr.getStatus() == ExecutionStatus.FAILED
                    || (sr.getExitCode() != null && sr.getExitCode() != 0)) {
                String stderr = (sr.getStderr() == null || sr.getStderr().isBlank())
                        ? "Runtime Error on test case " + tc.getId()
                        : sr.getStderr();
                return buildFailedSubmitResult(
                        request, totalTimeMs,
                        Verdict.RUNTIME_ERROR,
                        stderr,
                        sr.getStdout());
            }

            // ── 3. COMPARE OUTPUT ─────────────────────────────────────
            String expected = tc.getExpectedOutput() == null ? "" : tc.getExpectedOutput().trim();
            String actual   = sr.getStdout()         == null ? "" : sr.getStdout().trim();

            if (!expected.equals(actual)) {
                return buildFailedSubmitResult(
                        request, totalTimeMs,
                        Verdict.WRONG_ANSWER,
                        "Wrong Answer on test case " + tc.getId()
                                + " (expected='" + truncate(expected, 80)
                                + "', actual='"   + truncate(actual,   80) + "')",
                        sr.getStdout());
            }
        }

        // All test cases passed.
        return CodeExecutionResultEvent.builder()
                .jobId(request.getJobId())
                .submissionId(request.getSubmissionId())
                .userId(request.getUserId())
                .problemId(request.getProblemId())
                .verdict(Verdict.ACCEPTED)
                .status(ExecutionStatus.COMPLETED)
                .stdout("")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(totalTimeMs)
                .completedAt(Instant.now())
                .build();
    }

    private CodeExecutionResultEvent buildFailedSubmitResult(
            CodeExecutionRequestEvent request,
            long totalTimeMs,
            Verdict verdict,
            String stderr,
            String stdout) {
        return CodeExecutionResultEvent.builder()
                .jobId(request.getJobId())
                .submissionId(request.getSubmissionId())
                .userId(request.getUserId())
                .problemId(request.getProblemId())
                .verdict(verdict)
                .status(ExecutionStatus.FAILED)
                .stdout(stdout == null ? "" : stdout)
                .stderr(stderr)
                .exitCode(1)
                .executionTimeMs(totalTimeMs)
                .completedAt(Instant.now())
                .build();
    }

    // ════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════

    private ExecutionConfig buildExecutionConfig(CodeExecutionRequestEvent request, String stdin) {
        LanguageConfigResolver.LanguageProfile profile =
                languageConfigResolver.resolve(request.getLanguage());

        return ExecutionConfig.builder()
                .jobId(request.getJobId())
                .submissionId(request.getSubmissionId())
                .language(request.getLanguage())
                .sourceCode(request.getSourceCode())
                .stdinInput(stdin)
                .timeoutSeconds(request.getTimeoutSeconds())
                .memoryLimitBytes(256L * 1024 * 1024) // 256 MB
                .cpuPeriod(100_000L)
                .cpuQuota(50_000L)                    // 0.5 CPU
                .dockerImage(profile.dockerImage())
                .sourceFileName(profile.sourceFileName())
                .runCommand(profile.runCommand())
                .build();
    }

    private void setRedisStatus(UUID jobId, String status) {
        try {
            redisTemplate.opsForValue().set(
                    JOB_STATUS_KEY_PREFIX + jobId,
                    status,
                    JOB_STATUS_TTL);
        } catch (Exception ex) {
            log.warn("Failed to update Redis status for jobId={} to {}: {}",
                    jobId, status, ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\u2026";
    }
}