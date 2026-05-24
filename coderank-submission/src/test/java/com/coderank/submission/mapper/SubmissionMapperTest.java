package com.coderank.submission.mapper;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.submission.dto.response.SubmissionDetailResponse;
import com.coderank.submission.dto.response.SubmissionResponse;
import com.coderank.submission.entity.Submission;
import com.coderank.submission.enums.SubmissionType;
import com.coderank.submission.enums.Verdict;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SubmissionMapper")
class SubmissionMapperTest {

    private final SubmissionMapper mapper = new SubmissionMapper();
    private Submission submission;

    @BeforeEach
    void setUp() {
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
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();
    }

    // ------------------------------------------------------------------ //
    //  toResponse                                                          //
    // ------------------------------------------------------------------ //

    @Nested @DisplayName("toResponse")
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
            assertThat(response.getCreatedAt()).isEqualTo(submission.getCreatedAt());
        }

        @Test
        @DisplayName("does NOT expose sourceCode in lightweight response")
        void shouldNotExposeSourceCode() {
            SubmissionResponse response = mapper.toResponse(submission);
            // SubmissionResponse has no sourceCode field – compile-time guarantee.
            // We verify the mapper does not throw and returns a non-null object.
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("maps null problemId for RUN submission")
        void shouldMapNullProblemIdForRun() {
            submission = submission.toBuilder()
                    .problemId(null)
                    .submissionType(SubmissionType.RUN)
                    .build();

            SubmissionResponse response = mapper.toResponse(submission);
            assertThat(response.getProblemId()).isNull();
            assertThat(response.getSubmissionType()).isEqualTo(SubmissionType.RUN);
        }
    }

    // ------------------------------------------------------------------ //
    //  toDetailResponse                                                    //
    // ------------------------------------------------------------------ //

    @Nested @DisplayName("toDetailResponse")
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
            assertThat(detail.getCreatedAt()).isEqualTo(submission.getCreatedAt());
            assertThat(detail.getCompletedAt()).isEqualTo(submission.getCompletedAt());
        }

        @Test
        @DisplayName("exposes stdinInput for RUN submissions")
        void shouldExposeStdinForRun() {
            submission = submission.toBuilder()
                    .submissionType(SubmissionType.RUN)
                    .stdinInput("test input")
                    .build();

            SubmissionDetailResponse detail = mapper.toDetailResponse(submission);
            assertThat(detail.getStdinInput()).isEqualTo("test input");
        }

        @Test
        @DisplayName("maps null completedAt when submission not yet finished")
        void shouldMapNullCompletedAt() {
            submission = submission.toBuilder().completedAt(null).build();

            SubmissionDetailResponse detail = mapper.toDetailResponse(submission);
            assertThat(detail.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("maps PENDING verdict for in-flight submission")
        void shouldMapPendingVerdict() {
            submission = submission.toBuilder()
                    .status(ExecutionStatus.RUNNING)
                    .verdict(Verdict.PENDING)
                    .stdout(null)
                    .build();

            SubmissionDetailResponse detail = mapper.toDetailResponse(submission);
            assertThat(detail.getVerdict()).isEqualTo(Verdict.PENDING);
            assertThat(detail.getStdout()).isNull();
        }
    }
}
