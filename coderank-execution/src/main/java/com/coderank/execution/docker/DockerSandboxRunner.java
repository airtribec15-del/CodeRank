package com.coderank.execution.docker;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.execution.model.ExecutionConfig;
import com.coderank.execution.model.ExecutionResult;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.api.model.AccessMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs user code in an isolated Docker container.
 *
 * <p>Security controls applied to every container:
 * <ul>
 *   <li>Memory cap ({@code memoryLimitBytes})</li>
 *   <li>CPU quota (50 % of one core by default)</li>
 *   <li>Network disabled ({@code --network none})</li>
 *   <li>Read-only filesystem with a single writable {@code /code} tmpfs</li>
 *   <li>Dropped ALL Linux capabilities + no-new-privileges</li>
 *   <li>Run as unprivileged user (UID 1000)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerSandboxRunner {

    private final DockerClient dockerClient;

    /**
     * Synchronously executes the given {@link ExecutionConfig} inside a Docker container.
     *
     * @return {@link ExecutionResult} with stdout/stderr/exitCode/status
     */
    public ExecutionResult run(ExecutionConfig config) {
        Instant start = Instant.now();
        String containerId = null;

        try {
            // 1. Write source code to a temp directory on the host
            Path tmpDir = Files.createTempDirectory("coderank-" + config.getJobId());
            Path srcFile = tmpDir.resolve(config.getSourceFileName());
            Files.writeString(srcFile, config.getSourceCode(), StandardCharsets.UTF_8);

            // 2. Create container
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withMemory(config.getMemoryLimitBytes())
                    .withMemorySwap(config.getMemoryLimitBytes()) // disable swap
                    .withCpuPeriod(config.getCpuPeriod())
                    .withCpuQuota(config.getCpuQuota())
                    .withNetworkMode("none")
                    .withReadonlyRootfs(true)
                    .withBinds(new Bind(tmpDir.toAbsolutePath().toString(),
                            new Volume("/code"), AccessMode.rw))
                    .withCapDrop(Capability.ALL)
                    .withSecurityOpts(java.util.List.of("no-new-privileges:true"));

            CreateContainerResponse container = dockerClient.createContainerCmd(config.getDockerImage())
                    .withCmd("sh", "-c",
                            (config.getStdinInput() != null && !config.getStdinInput().isBlank()
                                    ? "echo '" + escapeShell(config.getStdinInput()) + "' | " : "")
                                    + config.getRunCommand())
                    .withHostConfig(hostConfig)
                    .withUser("1000:1000")
                    .withNetworkDisabled(true)
                    .withWorkingDir("/code")
                    .exec();

            containerId = container.getId();
            log.debug("Container {} created for jobId={}", containerId.substring(0, 12), config.getJobId());

            // 3. Start
            dockerClient.startContainerCmd(containerId).exec();

            // 4. Collect logs concurrently with a timeout
            StringBuilder stdoutBuf = new StringBuilder();
            StringBuilder stderrBuf = new StringBuilder();
            CountDownLatch logLatch = new CountDownLatch(1);
            AtomicReference<Exception> logError = new AtomicReference<>();

            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override public void onNext(Frame frame) {
                            String text = new String(frame.getPayload(), StandardCharsets.UTF_8);
                            if (frame.getStreamType() == StreamType.STDERR) stderrBuf.append(text);
                            else stdoutBuf.append(text);
                        }
                        @Override public void onError(Throwable throwable) {
                            logError.set(new RuntimeException(throwable));
                            logLatch.countDown();
                        }
                        @Override public void onComplete() { logLatch.countDown(); }
                    });

            // 5. Wait for container to exit with timeout
            boolean finished = dockerClient.waitContainerCmd(containerId)
                    .exec(new ResultCallback.Adapter<WaitResponse>())
                    .awaitCompletion(config.getTimeoutSeconds(), TimeUnit.SECONDS);

            logLatch.await(2, TimeUnit.SECONDS); // collect remaining log lines

            long elapsedMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            if (!finished) {
                log.warn("Container {} timed out after {}s for jobId={}",
                        containerId.substring(0, 12), config.getTimeoutSeconds(), config.getJobId());
                return ExecutionResult.builder()
                        .status(ExecutionStatus.TIMED_OUT)
                        .stdout(stdoutBuf.toString())
                        .stderr("Execution timed out after " + config.getTimeoutSeconds() + " seconds")
                        .exitCode(1)
                        .executionTimeMs(elapsedMs)
                        .completedAt(Instant.now())
                        .build();
            }

            // 6. Inspect exit code
            Integer exitCode = dockerClient.inspectContainerCmd(containerId)
                    .exec().getState().getExitCodeLong().intValue();

            ExecutionStatus status = exitCode == 0 ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED;

            // Clean up temp dir
            deleteTempDir(tmpDir);

            return ExecutionResult.builder()
                    .status(status)
                    .stdout(stdoutBuf.toString().trim())
                    .stderr(stderrBuf.toString().trim())
                    .exitCode(exitCode)
                    .executionTimeMs(elapsedMs)
                    .completedAt(Instant.now())
                    .build();

        } catch (Exception ex) {
            log.error("Docker execution failed for jobId={}: {}", config.getJobId(), ex.getMessage(), ex);
            return ExecutionResult.builder()
                    .status(ExecutionStatus.FAILED)
                    .stdout("")
                    .stderr("Internal execution error: " + ex.getMessage())
                    .exitCode(-1)
                    .executionTimeMs(Instant.now().toEpochMilli() - start.toEpochMilli())
                    .completedAt(Instant.now())
                    .build();
        } finally {
            if (containerId != null) {
                try {
                    dockerClient.stopContainerCmd(containerId).withTimeout(2).exec();
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                    log.debug("Container {} removed", containerId.substring(0, 12));
                } catch (Exception ignored) {
                    log.warn("Failed to remove container {}", containerId);
                }
            }
        }
    }

    // ------------------------------------------------------------------ //

    private String escapeShell(String input) {
        return input.replace("'", "'\"'\"'");
    }

    private void deleteTempDir(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
