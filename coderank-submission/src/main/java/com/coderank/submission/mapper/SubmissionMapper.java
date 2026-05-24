package com.coderank.submission.mapper;

import com.coderank.submission.dto.response.SubmissionDetailResponse;
import com.coderank.submission.dto.response.SubmissionResponse;
import com.coderank.submission.entity.Submission;
import org.springframework.stereotype.Component;

@Component
public class SubmissionMapper {

    public SubmissionResponse toResponse(Submission s) {
        return SubmissionResponse.builder()
                .submissionId(s.getId())
                .jobId(s.getJobId())
                .problemId(s.getProblemId())
                .language(s.getLanguage())
                .submissionType(s.getSubmissionType())
                .status(s.getStatus())
                .verdict(s.getVerdict())
                .createdAt(s.getCreatedAt())
                .build();
    }

    public SubmissionDetailResponse toDetailResponse(Submission s) {
        return SubmissionDetailResponse.builder()
                .submissionId(s.getId())
                .jobId(s.getJobId())
                .userId(s.getUserId())
                .problemId(s.getProblemId())
                .language(s.getLanguage())
                .submissionType(s.getSubmissionType())
                .status(s.getStatus())
                .verdict(s.getVerdict())
                .sourceCode(s.getSourceCode())
                .stdinInput(s.getStdinInput())
                .stdout(s.getStdout())
                .stderr(s.getStderr())
                .exitCode(s.getExitCode())
                .executionTimeMs(s.getExecutionTimeMs())
                .createdAt(s.getCreatedAt())
                .completedAt(s.getCompletedAt())
                .build();
    }
}
