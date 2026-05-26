package com.coderank.execution.docker;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.execution.model.ExecutionConfig;
import com.coderank.execution.model.ExecutionResult;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DockerSandboxRunner {

    private final DockerClient dockerClient;

    public ExecutionResult run(ExecutionConfig config) {
        Instant start = Instant.now();
        String containerId = null;

        try {
            // 1. Build the shell command.
            //
            //    The source file CANNOT be injected via copyArchiveToContainerCmd before
            //    startContainerCmd because Docker mounts tmpfs fresh at container start,
            //    wiping any files written into /code while the container was stopped.
            //
            //    Instead, the source code is written into /code/<sourceFileName> as the
            //    very first step of the shell command, after the tmpfs is live.
            //
            //    Full command structure:
            //      printf '%s' '<escaped-source>' > /code/<file> && [printf '%s' '<stdin>' |] ( <runCommand> )
            //
            //    CRITICAL — pipe operator precedence fix:
            //      Shell parses 'A && B | C && D' as '(A && (B | C)) && D'.
            //      Without the subshell, 'printf <stdin> | javac ... && java ...' attaches
            //      the pipe to javac only (which ignores stdin), leaving java with EOF on
            //      stdin — every submission reads null, outputs "", and gets WRONG_ANSWER.
            //      Wrapping runCommand in '( ... )' makes the pipe bind to the entire
            //      compound command, so stdin flows correctly into the runtime process.
            String writeFile = "printf '%s' " + shellSingleQuote(config.getSourceCode())
                    + " > /code/" + config.getSourceFileName();

            String stdinPrefix = (config.getStdinInput() != null && !config.getStdinInput().isBlank())
                    ? "printf '%s' " + shellSingleQuote(config.getStdinInput()) + " | "
                    : "";

            // Wrap runCommand in a subshell so the pipe (if present) binds to the
            // whole compound command, not just its first segment.
            String shellCmd = writeFile + " && " + stdinPrefix + "( " + config.getRunCommand() + " )";

            // 2. Create container.
            //    tmpfs uid=1000,gid=1000 so the unprivileged process owns /code and can
            //    write compiler output (.class files, compiled binaries) into it.
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withMemory(config.getMemoryLimitBytes())
                    .withMemorySwap(config.getMemoryLimitBytes())
                    .withCpuPeriod(config.getCpuPeriod())
                    .withCpuQuota(config.getCpuQuota())
                    .withNetworkMode("none")
                    .withTmpFs(java.util.Map.of("/code", "rw,nosuid,size=32m,uid=1000,gid=1000"))
                    .withCapDrop(Capability.ALL)
                    .withSecurityOpts(java.util.List.of("no-new-privileges:true"));

            CreateContainerResponse container = dockerClient.createContainerCmd(config.getDockerImage())
                    .withCmd("sh", "-c", shellCmd)
                    .withHostConfig(hostConfig)
                    .withUser("1000:1000")
                    .withNetworkDisabled(true)
                    .withWorkingDir("/code")
                    .exec();

            containerId = container.getId();
            log.debug("Container {} created for jobId={}", containerId.substring(0, 12), config.getJobId());

            // 3. Register the wait callback BEFORE starting the container.
            //    This eliminates the race condition where a fast-exiting container
            //    (e.g., print("hello") finishing in <100 ms) completes before
            //    awaitCompletion() is reached, causing it to return false and
            //    misreport the result as TIMEDOUT.
            ResultCallback.Adapter<WaitResponse> waitCallback = new ResultCallback.Adapter<>();
            dockerClient.waitContainerCmd(containerId).exec(waitCallback);

            // 4. Start the container.
            dockerClient.startContainerCmd(containerId).exec();

            // 5. Collect logs (registered after start; fast containers may have already
            //    written output — withFollowStream(true) drains whatever is buffered).
            StringBuilder stdoutBuf = new StringBuilder();
            StringBuilder stderrBuf = new StringBuilder();
            CountDownLatch logLatch = new CountDownLatch(1);

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
                        @Override public void onComplete() { logLatch.countDown(); }
                        @Override public void onError(Throwable t) { logLatch.countDown(); }
                    });

            // 6. Wait for container to exit (wait callback was registered before start,
            //    so this correctly catches fast-exiting containers).
            boolean finished = waitCallback.awaitCompletion(config.getTimeoutSeconds(), TimeUnit.SECONDS);

            logLatch.await(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            log.debug("[sandbox] jobId={} exitCode={} stdout=[{}] stderr=[{}]",
                    config.getJobId(),
                    "pending-inspect",
                    stdoutBuf.toString().replace("\n", "\\n"),
                    stderrBuf.toString().replace("\n", "\\n"));
            long elapsedMs = Instant.now().toEpochMilli() - start.toEpochMilli();

            if (!finished) {
                log.warn("Container {} timed out after {}s for jobId={}",
                        containerId.substring(0, 12), config.getTimeoutSeconds(), config.getJobId());
                return ExecutionResult.builder()
                        .status(ExecutionStatus.TIMEDOUT)
                        .stdout(stdoutBuf.toString())
                        .stderr("Execution timed out after " + config.getTimeoutSeconds() + " seconds")
                        .exitCode(1)
                        .executionTimeMs(elapsedMs)
                        .completedAt(Instant.now())
                        .build();
            }

            // 7. Inspect exit code.
            Integer exitCode = dockerClient.inspectContainerCmd(containerId)
                    .exec().getState().getExitCodeLong().intValue();

            ExecutionStatus status = exitCode == 0 ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED;

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
                // FIX: stop and remove are in separate try-catch blocks.
                // Previously both were inside one block — a stopContainerCmd
                // exception (thrown when the container has already exited on
                // its own) would jump to the catch and skip removeContainerCmd
                // entirely, leaking the container. Now stop failure is silently
                // ignored and remove always executes independently.
                try {
                    dockerClient.stopContainerCmd(containerId).withTimeout(2).exec();
                } catch (Exception ignored) { }
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                    log.debug("Container {} removed", containerId.substring(0, 12));
                } catch (Exception e) {
                    log.warn("Failed to remove container {}", containerId);
                }
            }
        }
    }

    /**
     * Wraps input in single quotes for safe shell passing via printf.
     * Handles embedded single quotes by breaking out of the quote:  ' → '"'"'
     *
     * Example:  it's  →  'it'"'"'s'
     */
    private String shellSingleQuote(String input) {
        return "'" + input.replace("'", "'\"'\"'") + "'";
    }
}