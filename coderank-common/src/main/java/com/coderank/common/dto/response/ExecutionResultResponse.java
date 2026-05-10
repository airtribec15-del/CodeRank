package com.coderank.common.dto.response;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResultResponse {

    private UUID submissionId;
    private UUID jobId;
    private Language language;
    private ExecutionStatus status;
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