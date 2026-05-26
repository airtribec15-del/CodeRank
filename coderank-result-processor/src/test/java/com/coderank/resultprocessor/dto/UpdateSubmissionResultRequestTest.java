package com.coderank.resultprocessor.dto;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UpdateSubmissionResultRequest — Lombok DTO smoke tests")
class UpdateSubmissionResultRequestTest {

    @Test
    @DisplayName("Builder populates all fields correctly")
    void builderPopulatesFields() {
        UUID jobId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-01-01T10:00:00Z");

        UpdateSubmissionResultRequest req = UpdateSubmissionResultRequest.builder()
                .jobId(jobId)
                .submissionId(submissionId)
                .status(ExecutionStatus.COMPLETED)
                .verdict(Verdict.ACCEPTED)
                .stdout("hello")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(150L)
                .completedAt(completedAt)
                .build();

        assertThat(req.getJobId()).isEqualTo(jobId);
        assertThat(req.getSubmissionId()).isEqualTo(submissionId);
        assertThat(req.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(req.getVerdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(req.getStdout()).isEqualTo("hello");
        assertThat(req.getStderr()).isEqualTo("");
        assertThat(req.getExitCode()).isEqualTo(0);
        assertThat(req.getExecutionTimeMs()).isEqualTo(150L);
        assertThat(req.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("NoArgsConstructor + setters work")
    void noArgsAndSetters() {
        UpdateSubmissionResultRequest req = new UpdateSubmissionResultRequest();
        UUID jobId = UUID.randomUUID();
        req.setJobId(jobId);
        req.setStatus(ExecutionStatus.FAILED);

        assertThat(req.getJobId()).isEqualTo(jobId);
        assertThat(req.getStatus()).isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    @DisplayName("equals/hashCode reflect all fields")
    void equalsAndHashCode() {
        UUID jobId = UUID.randomUUID();
        UpdateSubmissionResultRequest a = UpdateSubmissionResultRequest.builder()
                .jobId(jobId).status(ExecutionStatus.COMPLETED).build();
        UpdateSubmissionResultRequest b = UpdateSubmissionResultRequest.builder()
                .jobId(jobId).status(ExecutionStatus.COMPLETED).build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("Nullable verdict is supported (RUN-type jobs)")
    void nullVerdictAllowed() {
        UpdateSubmissionResultRequest req = UpdateSubmissionResultRequest.builder()
                .jobId(UUID.randomUUID())
                .submissionId(UUID.randomUUID())
                .status(ExecutionStatus.COMPLETED)
                .verdict(null)
                .build();

        assertThat(req.getVerdict()).isNull();
    }
}