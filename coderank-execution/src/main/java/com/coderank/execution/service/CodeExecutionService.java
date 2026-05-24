package com.coderank.execution.service;

import com.coderank.common.constants.RedisKeys;
import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.event.CodeExecutionRequestEvent;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.execution.docker.DockerSandboxRunner;
import com.coderank.execution.model.ExecutionConfig;
import com.coderank.execution.model.ExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static com.coderank.common.constants.KafkaTopics.EXECUTION_RESULTS;

/**
 * Orchestrates the end-to-end execution pipeline for a single
 * {@link CodeExecutionRequestEvent}:
 *
 * <ol>
 *   <li>Mark job as RUNNING in Redis</li>
 *   <li>Resolve Docker image + run command for the language</li>
 *   <li>Delegate to {@link DockerSandboxRunner}</li>
 *   <li>Publish {@link CodeExecutionResultEvent} to {@code code.execution.results}</li>
 *   <li>Update Redis job status</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeExecutionService {

    private final DockerSandboxRunner sandboxRunner;
    private final LanguageConfigResolver languageConfigResolver;
    private final KafkaTemplate<String, CodeExecutionResultEvent> resultKafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${execution.docker.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${execution.docker.memory-limit-mb:128}")
    private int memoryLimitMb;

    @Value("${execution.docker.cpu-period:100000}")
    private long cpuPeriod;

    @Value("${execution.docker.cpu-quota:50000}")
    private long cpuQuota;

    /**
     * Execute the submission asynchronously so the Kafka listener thread is not blocked.
     * Spring's {@code @Async} uses the task executor thread pool.
     */
    @Async
    public void executeAsync(CodeExecutionRequestEvent request) {
        log.info("Starting execution for jobId={} language={}", request.getJobId(), request.getLanguage());

        // Mark RUNNING in Redis so polling endpoints show live status
        updateRedisStatus(request.getJobId().toString(), ExecutionStatus.RUNNING);

        LanguageConfigResolver.LanguageProfile profile =
                languageConfigResolver.resolve(request.getLanguage());

        ExecutionConfig config = ExecutionConfig.builder()
                .jobId(request.getJobId())
                .submissionId(request.getSubmissionId())
                .language(request.getLanguage())
                .sourceCode(request.getSourceCode())
                .stdinInput(request.getStdinInput())
                .timeoutSeconds(request.getTimeoutSeconds() > 0
                        ? request.getTimeoutSeconds() : timeoutSeconds)
                .memoryLimitBytes((long) memoryLimitMb * 1024 * 1024)
                .cpuPeriod(cpuPeriod)
                .cpuQuota(cpuQuota)
                .dockerImage(profile.dockerImage())
                .sourceFileName(profile.sourceFileName())
                .runCommand(profile.runCommand())
                .build();

        ExecutionResult result = sandboxRunner.run(config);

        // Publish result event
        CodeExecutionResultEvent resultEvent = CodeExecutionResultEvent.builder()
                .jobId(request.getJobId())
                .submissionId(request.getSubmissionId())
                .status(result.getStatus())
                .stdout(result.getStdout())
                .stderr(result.getStderr())
                .exitCode(result.getExitCode())
                .executionTimeMs(result.getExecutionTimeMs())
                .completedAt(result.getCompletedAt())
                .build();

        resultKafkaTemplate.send(EXECUTION_RESULTS, request.getJobId().toString(), resultEvent);
        log.info("Published result for jobId={} status={}", request.getJobId(), result.getStatus());

        // Update Redis with final status
        updateRedisStatus(request.getJobId().toString(), result.getStatus());
    }

    // ------------------------------------------------------------------ //

    private void updateRedisStatus(String jobId, ExecutionStatus status) {
        String key = RedisKeys.jobStatusKey(jobId);
        redisTemplate.opsForValue().set(key, status.name(), Duration.ofHours(2));
    }

    // ------------------------------------------------------------------ //
    //  DLT fallback — called by DLT handler                              //
    // ------------------------------------------------------------------ //

    /**
     * Publishes a FAILED {@link CodeExecutionResultEvent} to the results topic
     * so that the submission consumer can update the row from PENDING → FAILED.
     * Called by the DLT handler as a best-effort cleanup when all retries are exhausted.
     */
    public void publishFailedResult(java.util.UUID jobId, java.util.UUID submissionId, String errorMessage) {
        CodeExecutionResultEvent failedEvent = CodeExecutionResultEvent.builder()
                .jobId(jobId)
                .submissionId(submissionId)
                .status(com.coderank.common.enums.ExecutionStatus.FAILED)
                .stdout("")
                .stderr(errorMessage)
                .exitCode(-1)
                .executionTimeMs(0L)
                .completedAt(java.time.Instant.now())
                .build();

        resultKafkaTemplate.send(EXECUTION_RESULTS, jobId.toString(), failedEvent);
        log.warn("[DLT-FALLBACK] Published FAILED result for jobId={}", jobId);

        updateRedisStatus(jobId.toString(), com.coderank.common.enums.ExecutionStatus.FAILED);
    }
}
