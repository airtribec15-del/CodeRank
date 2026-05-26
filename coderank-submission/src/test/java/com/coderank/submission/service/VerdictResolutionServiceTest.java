// src/test/java/com/coderank/submission/service/VerdictResolutionServiceTest.java
package com.coderank.submission.service;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.coderank.common.event.CodeExecutionResultEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerdictResolutionService")
class VerdictResolutionServiceTest {

    private final VerdictResolutionService service = new VerdictResolutionService();

    private CodeExecutionResultEvent event(ExecutionStatus status, String stdout,
                                           String stderr, Integer exitCode) {
        return CodeExecutionResultEvent.builder()
                .jobId(UUID.randomUUID())
                .submissionId(UUID.randomUUID())
                .status(status)
                .stdout(stdout)
                .stderr(stderr)
                .exitCode(exitCode)
                .executionTimeMs(100L)
                .completedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("TIMEDOUT → TIME_LIMIT_EXCEEDED")
    void timedOutMapsToTle() {
        assertThat(service.resolve(event(ExecutionStatus.TIMEDOUT, "", "", 1)))
                .isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
    }

    @ParameterizedTest(name = "stderr={0}")
    @ValueSource(strings = {
            "CompilationError: unexpected token",
            "compilation error in line 3",
            "SyntaxError: invalid syntax",
            "error: cannot find symbol",
            "error: expected ';'",                  // FIXED: production checks literal "error: expected"
            "NameError: name 'x' is not defined",
            "ModuleNotFoundError: No module named 'foo'"
    })
    @DisplayName("FAILED with compilation keywords → COMPILATION_ERROR")
    void compilationKeywordsMapToCompilationError(String stderr) {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, "", stderr, 1)))
                .isEqualTo(Verdict.COMPILATION_ERROR);
    }

    @Test
    @DisplayName("compilation error is detected even when status is COMPLETED (edge case)")
    void compilationErrorOverridesCompleted() {
        CodeExecutionResultEvent e = event(ExecutionStatus.COMPLETED, "",
                "SyntaxError: unexpected indent", 0);
        assertThat(service.resolve(e)).isEqualTo(Verdict.COMPILATION_ERROR);
    }

    @Test
    @DisplayName("FAILED with 'wrong answer' in stderr → WRONG_ANSWER")
    void failedWithWrongAnswerStderr() {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, "2", "Wrong Answer", 0)))
                .isEqualTo(Verdict.WRONG_ANSWER);
    }

    @Test
    @DisplayName("FAILED with 'time limit' in stderr → TIME_LIMIT_EXCEEDED")
    void failedWithTimeLimitStderr() {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, "", "Time limit exceeded", 1)))
                .isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("FAILED with non-zero exit code → RUNTIME_ERROR")
    void failedWithNonZeroExitCode() {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, "", "Segfault", 139)))
                .isEqualTo(Verdict.RUNTIME_ERROR);
    }

    @Test
    @DisplayName("FAILED with zero exit code and no stderr hint → RUNTIME_ERROR (fallback)")
    void failedWithNoHintFallsBackToRuntimeError() {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, "", "", 0)))
                .isEqualTo(Verdict.RUNTIME_ERROR);
    }

    @Test
    @DisplayName("FAILED with null exit code and null stderr → RUNTIME_ERROR (null-safe)")
    void failedWithNullStderrAndNullExit() {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, null, null, null)))
                .isEqualTo(Verdict.RUNTIME_ERROR);
    }

    @Test
    @DisplayName("FAILED with null stderr and non-zero exit → RUNTIME_ERROR")
    void failedWithNullStderr() {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, null, null, 1)))
                .isEqualTo(Verdict.RUNTIME_ERROR);
    }

    @Test
    @DisplayName("COMPLETED with no error markers → ACCEPTED")
    void completedMapsToAccepted() {
        assertThat(service.resolve(event(ExecutionStatus.COMPLETED, "[0,1]", "", 0)))
                .isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    @DisplayName("COMPLETED with null stderr → ACCEPTED (null-safe)")
    void completedWithNullStderr() {
        assertThat(service.resolve(event(ExecutionStatus.COMPLETED, "output", null, 0)))
                .isEqualTo(Verdict.ACCEPTED);
    }

    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = ExecutionStatus.class, names = {"QUEUED", "RUNNING"})
    @DisplayName("QUEUED / RUNNING → PENDING (still in flight)")
    void inFlightStatusMapsToPending(ExecutionStatus status) {
        assertThat(service.resolve(event(status, null, null, null)))
                .isEqualTo(Verdict.PENDING);
    }

    @Test
    @DisplayName("TIMEDOUT takes priority over compilation error keywords in stderr")
    void timedOutTakesPriorityOverCompilationError() {
        CodeExecutionResultEvent e = event(ExecutionStatus.TIMEDOUT, "",
                "compilation error", 1);
        assertThat(service.resolve(e)).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
    }

    @ParameterizedTest(name = "stderr=''{0}''")
    @ValueSource(strings = {"WRONG ANSWER", "wrong answer", "Wrong Answer", "WrOnG AnSwEr"})
    @DisplayName("Wrong Answer detection is case-insensitive")
    void wrongAnswerDetectionCaseInsensitive(String stderr) {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, "3", stderr, 0)))
                .isEqualTo(Verdict.WRONG_ANSWER);
    }

    @ParameterizedTest(name = "stderr=''{0}''")
    @ValueSource(strings = {"TIME LIMIT EXCEEDED", "time limit", "Time Limit Exceeded"})
    @DisplayName("Time Limit detection in stderr is case-insensitive")
    void timeLimitDetectionCaseInsensitive(String stderr) {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, "", stderr, 1)))
                .isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("WRONG_ANSWER hint takes priority over non-zero exit code")
    void wrongAnswerHintBeatsExitCode() {
        assertThat(service.resolve(event(ExecutionStatus.FAILED, "5", "Wrong Answer", 1)))
                .isEqualTo(Verdict.WRONG_ANSWER);
    }
}