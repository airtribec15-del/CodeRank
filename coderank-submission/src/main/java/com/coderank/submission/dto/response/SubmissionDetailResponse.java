package com.coderank.submission.dto.response;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.submission.enums.SubmissionType;
import com.coderank.submission.enums.Verdict;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Full response including source code, stdout, stderr, timing, and verdict. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubmissionDetailResponse {
    private UUID submissionId;
    private UUID jobId;
    private UUID userId;
    private UUID problemId;
    private Language language;
    private SubmissionType submissionType;
    private ExecutionStatus status;
    private Verdict verdict;
    private String sourceCode;
    private String stdinInput;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private Long executionTimeMs;

    @JsonSerialize(using = InstantSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonSerialize(using = InstantSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant completedAt;
}
