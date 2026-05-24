package com.coderank.execution.model;

import com.coderank.common.enums.ExecutionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Raw output from a Docker container run.
 */
@Data
@Builder
public class ExecutionResult {
    private ExecutionStatus status;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private Long executionTimeMs;
    private Instant completedAt;
}
