package com.coderank.submission.entity;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.common.enums.Verdict;
import com.coderank.submission.enums.SubmissionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "submissions", indexes = {
        @Index(name = "idx_submissions_user_id",   columnList = "user_id"),
        @Index(name = "idx_submissions_problem_id", columnList = "problem_id"),
        @Index(name = "idx_submissions_job_id",     columnList = "job_id", unique = true),
        @Index(name = "idx_submissions_status",     columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The authenticated user who submitted. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The problem this submission belongs to. NULL for RUN (ad-hoc) submissions. */
    @Column(name = "problem_id")
    private UUID problemId;

    /** Kafka job correlation ID. Used to match the execution result event back to this row. */
    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_type", nullable = false, length = 10)
    private SubmissionType submissionType;

    @Column(name = "source_code", nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    /** Custom stdin for RUN mode — NULL for SUBMIT mode. */
    @Column(name = "stdin_input", columnDefinition = "TEXT")
    private String stdinInput;

    @Column(columnDefinition = "TEXT")
    private String stdout;

    @Column(columnDefinition = "TEXT")
    private String stderr;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(length = 25)
    @Builder.Default
    private Verdict verdict = Verdict.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}