package com.coderank.submission.controller;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.submission.enums.Verdict;
import com.coderank.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal endpoints consumed ONLY by the Result Processor.
 * Blocked at the API Gateway for all external traffic.
 * Path prefix: /api/v1/internal/submissions
 */
@RestController
@RequestMapping("/api/v1/internal/submissions")
@RequiredArgsConstructor
@Tag(name = "Internal - Submissions", description = "Internal endpoints for service-to-service communication only")
public class InternalSubmissionController {

    private final SubmissionService submissionService;

    /**
     * PATCH /api/v1/internal/submissions/result
     * Called by the Result Processor to write execution outcome back.
     */
    @PatchMapping("/result")
    @Operation(summary = "Update submission result – Result Processor only")
    public ResponseEntity<Void> updateResult(
            @RequestParam UUID jobId,
            @RequestParam ExecutionStatus status,
            @RequestParam(required = false) String stdout,
            @RequestParam(required = false) String stderr,
            @RequestParam(required = false) Integer exitCode,
            @RequestParam(required = false) Long executionTimeMs,
            @RequestParam(required = false, defaultValue = "PENDING") Verdict verdict) {

        submissionService.updateSubmissionResult(
                jobId, status, stdout, stderr, exitCode, executionTimeMs, verdict);
        return ResponseEntity.noContent().build();
    }
}
