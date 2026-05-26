package com.coderank.submission.service;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionResultEvent;
import org.springframework.stereotype.Service;

/**
 * Translates a raw {@link CodeExecutionResultEvent} into a {@link Verdict}.
 *
 * <p>Rules in priority order:
 * <ol>
 *   <li>TIMED_OUT → TIME_LIMIT_EXCEEDED</li>
 *   <li>stderr contains compilation error keywords → COMPILATION_ERROR</li>
 *   <li>FAILED + stderr hints → WRONG_ANSWER / TIME_LIMIT_EXCEEDED</li>
 *   <li>FAILED + non-zero exit code → RUNTIME_ERROR</li>
 *   <li>COMPLETED → ACCEPTED</li>
 *   <li>FAILED (fallback) → RUNTIME_ERROR</li>
 *   <li>Anything else (still in-flight) → PENDING</li>
 * </ol>
 */
@Service
public class VerdictResolutionService {

    public Verdict resolve(CodeExecutionResultEvent event) {
        ExecutionStatus status = event.getStatus();

        if (status == ExecutionStatus.TIMEDOUT) return Verdict.TIME_LIMIT_EXCEEDED;
        if (isCompilationError(event))           return Verdict.COMPILATION_ERROR;
        if (status == ExecutionStatus.FAILED)    return resolveFailedVerdict(event);
        if (status == ExecutionStatus.COMPLETED) return Verdict.ACCEPTED;

        // QUEUED / RUNNING — still in flight
        return Verdict.PENDING;
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private boolean isCompilationError(CodeExecutionResultEvent event) {
        if (event.getStderr() == null) return false;
        String stderr = event.getStderr().toLowerCase();
        return stderr.contains("compilationerror")
                || stderr.contains("compilation error")
                || stderr.contains("syntaxerror")
                || stderr.contains("error: cannot find symbol")
                || stderr.contains("error: expected")
                || stderr.contains("nameerror")
                || stderr.contains("modulenotfounderror");
    }

    private Verdict resolveFailedVerdict(CodeExecutionResultEvent event) {
        // Result Processor may embed a verdict hint in stderr
        if (event.getStderr() != null) {
            String stderr = event.getStderr().toLowerCase();
            if (stderr.contains("wrong answer"))  return Verdict.WRONG_ANSWER;
            if (stderr.contains("time limit"))    return Verdict.TIME_LIMIT_EXCEEDED;
        }
        // Non-zero exit code → runtime error
        if (event.getExitCode() != null && event.getExitCode() != 0) return Verdict.RUNTIME_ERROR;
        return Verdict.RUNTIME_ERROR;
    }
}