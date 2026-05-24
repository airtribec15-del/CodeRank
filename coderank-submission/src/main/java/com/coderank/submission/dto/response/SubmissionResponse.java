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

/** Lightweight response returned immediately after accepting a submission. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubmissionResponse {
    private UUID submissionId;
    private UUID jobId;
    private UUID problemId;
    private Language language;
    private SubmissionType submissionType;
    private ExecutionStatus status;
    private Verdict verdict;

    @JsonSerialize(using = InstantSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
