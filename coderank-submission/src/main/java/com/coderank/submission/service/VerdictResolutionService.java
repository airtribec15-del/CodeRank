package com.coderank.submission.service;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.event.CodeExecutionResultEvent;
import com.coderank.submission.enums.Verdict;
import org.springframework.stereotype.Service;

/**
 * Translates a raw {@link CodeExecutionResultEvent} into a {@link Verdict}.
 *
 * <p>Rules (in priority order):
 * <ol>
 *   <li>TIMED_OUT  → TIME_LIMIT_EXCEEDED</li>
 *   <li>exitCode != 0 (non-zero exit)  → RUNTIME_ERROR</li>
 *   <li>stderr contains compilation error keywords → COMPILATION_ERROR</li>
 *   <li>COMPLETED  → ACCEPTED  (full test-case match is done by Result Processor;
 *       here we trust the event status for RUN submissions)</li>
 *   <li>FAILED     → RUNTIME_ERROR (catchall)</li>
 *   <li>Anything else still in-flight → PENDING</li>
 * </ol>
 *
 * <p>For SUBMIT-type submissions the Result Processor sets the status to
 * COMPLETED only when all test cases pass (ACCEPTED). It sets it to FAILED
 * with a meaningful stderr/stdout when any test case fails, so this service
 * maps FAILED + stderr containing "Wrong Answer" → WRONG_ANSWER, etc.
 */
@Service
public class VerdictResolutionService {

    public Verdict resolve(CodeExecutionResultEvent event) {
        ExecutionStatus status = event.getStatus();

        if (status == ExecutionStatus.TIMED_OUT) {
            return Verdict.TIME_LIMIT_EXCEEDED;
        }

        if (isCompilationError(event)) {
            return Verdict.COMPILATION_ERROR;
        }

        if (status == ExecutionStatus.FAILED) {
            return resolveFailedVerdict(event);
        }

        if (status == ExecutionStatus.COMPLETED) {
            return Verdict.ACCEPTED;
        }

        // QUEUED / RUNNING – still in flight
        return Verdict.PENDING;
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    private boolean isCompilationError(CodeExecutionResultEvent event) {
        if (event.getStderr() == null) return false;
        String stderr = event.getStderr().toLowerCase();
        return stderr.contains("compilationerror")
                || stderr.contains("compilation error")
                || stderr.contains("syntaxerror")
                || stderr.contains("error: cannot find symbol")
                || stderr.contains("error: ';' expected")
                || stderr.contains("nameerror")
                || stderr.contains("modulenotfounderror");
    }

    private Verdict resolveFailedVerdict(CodeExecutionResultEvent event) {
        // Result Processor may embed a verdict hint in stderr
        if (event.getStderr() != null) {
            String stderr = event.getStderr().toLowerCase();
            if (stderr.contains("wrong answer")) {
                return Verdict.WRONG_ANSWER;
            }
            if (stderr.contains("time limit")) {
                return Verdict.TIME_LIMIT_EXCEEDED;
            }
        }
        // Non-zero exit code → runtime error
        if (event.getExitCode() != null && event.getExitCode() != 0) {
            return Verdict.RUNTIME_ERROR;
        }
        // Fallback
        return Verdict.RUNTIME_ERROR;
    }
}
