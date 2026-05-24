package com.coderank.execution.model;

import com.coderank.common.enums.Language;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * All parameters needed to spin up and run a Docker container for one submission.
 */
@Data
@Builder
public class ExecutionConfig {
    private UUID jobId;
    private UUID submissionId;
    private Language language;
    private String sourceCode;
    private String stdinInput;
    private int timeoutSeconds;
    private long memoryLimitBytes;
    private long cpuPeriod;
    private long cpuQuota;
    /** Docker image tag, e.g. "python:3.11-slim" */
    private String dockerImage;
    /** File name for the source inside the container, e.g. "Main.java" */
    private String sourceFileName;
    /** Shell command to compile + run, e.g. "python3 /code/solution.py" */
    private String runCommand;
}
