// src/test/java/com/coderank/submission/mapper/SubmissionMapperTest.java
package com.coderank.submission.mapper;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.common.enums.Verdict;
import com.coderank.submission.dto.response.SubmissionDetailResponse;
import com.coderank.submission.dto.response.SubmissionResponse;
import com.coderank.submission.entity.Submission;
import com.coderank.submission.enums.SubmissionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubmissionMapper")
class SubmissionMapperTest {

    private final SubmissionMapper mapper = new SubmissionMapper();
    private Submission submission;
    private Instant createdAt;
    private Instant completedAt;

    @BeforeEach
    void setUp() {
        createdAt = Instant.now();
        completedAt = createdAt.plusMillis(150);
        submission = Submission.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .problemId(UUID.randomUUID())
                .jobId(UUID.randomUUID())
                .language(Language.JAVA)
                .submissionType(SubmissionType.SUBMIT)
                .sourceCode("class S{}")
                .stdinInput(null)
                .stdout("[0,1]")
                .stderr("")
                .exitCode(0)
                .executionTimeMs(120L)
                .status(ExecutionStatus.COMPLETED)
                .verdict(Verdict.ACCEPTED)
                .createdAt(createdAt)
                .completedAt(completedAt)
                .build();
    }

    @Nested
    @DisplayName("toResponse")
    class ToResponse {

        @Test
        @DisplayName("maps all lightweight fields correctly")
        void shouldMapAllFields() {
            SubmissionResponse response = mapper.toResponse(submission);

            assertThat(response.getSubmissionId()).isEqualTo(submission.getId());
            assertThat(response.getJobId()).isEqualTo(submission.getJobId());
            assertThat(response.getProblemId()).isEqualTo(submission.getProblemId());
            assertThat(response.getLanguage()).isEqualTo(Language.JAVA);
            assertThat(response.getSubmissionType()).isEqualTo(SubmissionType.SUBMIT);
            assertThat(response.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(response.getVerdict()).isEqualTo(Verdict.ACCEPTED);
            assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("maps null problemId for RUN submission")
        void shouldMapNullProblemIdForRun() {
            Submission run = Submission.builder()
                    .id(submission.getId())
                    .userId(submission.getUserId())
                    .jobId(submission.getJobId())
                    .language(Language.PYTHON)
                    .submissionType(SubmissionType.RUN)
                    .sourceCode("print(1)")
                    .status(ExecutionStatus.QUEUED)
                    .verdict(Verdict.PENDING)
                    .build();

            SubmissionResponse response = mapper.toResponse(run);

            assertThat(response.getProblemId()).isNull();
            assertThat(response.getSubmissionType()).isEqualTo(SubmissionType.RUN);
        }
    }

    @Nested
    @DisplayName("toDetailResponse")
    class ToDetailResponse {

        @Test
        @DisplayName("maps all detail fields including sourceCode, stdout, stderr")
        void shouldMapAllDetailFields() {
            SubmissionDetailResponse detail = mapper.toDetailResponse(submission);

            assertThat(detail.getSubmissionId()).isEqualTo(submission.getId());
            assertThat(detail.getUserId()).isEqualTo(submission.getUserId());
            assertThat(detail.getJobId()).isEqualTo(submission.getJobId());
            assertThat(detail.getProblemId()).isEqualTo(submission.getProblemId());
            assertThat(detail.getLanguage()).isEqualTo(Language.JAVA);
            assertThat(detail.getSubmissionType()).isEqualTo(SubmissionType.SUBMIT);
            assertThat(detail.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(detail.getVerdict()).isEqualTo(Verdict.ACCEPTED);
            assertThat(detail.getSourceCode()).isEqualTo("class S{}");
            assertThat(detail.getStdout()).isEqualTo("[0,1]");
            assertThat(detail.getStderr()).isEmpty();
            assertThat(detail.getExitCode()).isEqualTo(0);
            assertThat(detail.getExecutionTimeMs()).isEqualTo(120L);
            assertThat(detail.getCreatedAt()).isEqualTo(createdAt);
            assertThat(detail.getCompletedAt()).isEqualTo(completedAt);
        }

        @Test
        @DisplayName("exposes stdinInput for RUN submissions")
        void shouldExposeStdinForRun() {
            Submission run = Submission.builder()
                    .id(submission.getId())
                    .userId(submission.getUserId())
                    .jobId(submission.getJobId())
                    .language(Language.PYTHON)
                    .submissionType(SubmissionType.RUN)
                    .sourceCode("input()")
                    .stdinInput("test input")
                    .status(ExecutionStatus.QUEUED)
                    .verdict(Verdict.PENDING)
                    .build();

            SubmissionDetailResponse detail = mapper.toDetailResponse(run);

            assertThat(detail.getStdinInput()).isEqualTo("test input");
            assertThat(detail.getSubmissionType()).isEqualTo(SubmissionType.RUN);
        }

        @Test
        @DisplayName("maps null completedAt when submission not yet finished")
        void shouldMapNullCompletedAt() {
            Submission inFlight = Submission.builder()
                    .id(submission.getId())
                    .userId(submission.getUserId())
                    .jobId(submission.getJobId())
                    .language(Language.PYTHON)
                    .submissionType(SubmissionType.SUBMIT)
                    .sourceCode("print(1)")
                    .status(ExecutionStatus.RUNNING)
                    .verdict(Verdict.PENDING)
                    .createdAt(createdAt)
                    .completedAt(null)
                    .build();

            SubmissionDetailResponse detail = mapper.toDetailResponse(inFlight);

            assertThat(detail.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("maps PENDING verdict for in-flight submission")
        void shouldMapPendingVerdict() {
            Submission inFlight = Submission.builder()
                    .id(submission.getId())
                    .userId(submission.getUserId())
                    .jobId(submission.getJobId())
                    .language(Language.PYTHON)
                    .submissionType(SubmissionType.SUBMIT)
                    .sourceCode("print(1)")
                    .status(ExecutionStatus.RUNNING)
                    .verdict(Verdict.PENDING)
                    .stdout(null)
                    .build();

            SubmissionDetailResponse detail = mapper.toDetailResponse(inFlight);

            assertThat(detail.getVerdict()).isEqualTo(Verdict.PENDING);
            assertThat(detail.getStdout()).isNull();
        }

        @Test
        @DisplayName("maps null exitCode and null executionTimeMs gracefully")
        void shouldMapNullExitAndTime() {
            Submission s = Submission.builder()
                    .id(submission.getId())
                    .userId(submission.getUserId())
                    .jobId(submission.getJobId())
                    .language(Language.CPP)
                    .submissionType(SubmissionType.SUBMIT)
                    .sourceCode("int main(){}")
                    .status(ExecutionStatus.QUEUED)
                    .verdict(Verdict.PENDING)
                    .exitCode(null)
                    .executionTimeMs(null)
                    .build();

            SubmissionDetailResponse detail = mapper.toDetailResponse(s);

            assertThat(detail.getExitCode()).isNull();
            assertThat(detail.getExecutionTimeMs()).isNull();
        }
    }
}