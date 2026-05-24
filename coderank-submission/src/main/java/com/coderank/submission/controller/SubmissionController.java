package com.coderank.submission.controller;

import com.coderank.submission.dto.request.RunRequest;
import com.coderank.submission.dto.request.SubmitRequest;
import com.coderank.submission.dto.response.SubmissionDetailResponse;
import com.coderank.submission.dto.response.SubmissionResponse;
import com.coderank.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Submissions", description = "Code execution and submission endpoints")
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * POST /api/v1/execute
     * Ad-hoc code run with custom stdin. Does NOT judge against test cases.
     */
    @PostMapping("/execute")
    @Operation(summary = "Run code (ad-hoc, no judging)")
    public ResponseEntity<SubmissionResponse> execute(
            @Valid @RequestBody RunRequest request,
            @AuthenticationPrincipal String userId) {

        SubmissionResponse response = submissionService.run(request, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * POST /api/v1/submissions
     * Full judge submission against all problem test cases.
     */
    @PostMapping("/submissions")
    @Operation(summary = "Submit code for judging against a problem")
    public ResponseEntity<SubmissionResponse> submit(
            @Valid @RequestBody SubmitRequest request,
            @AuthenticationPrincipal String userId) {

        SubmissionResponse response = submissionService.submit(request, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * GET /api/v1/submissions/{id}
     * Retrieve a specific submission by ID.
     * Users can only see their own; admins can see all.
     */
    @GetMapping("/submissions/{id}")
    @Operation(summary = "Get submission by ID")
    public ResponseEntity<SubmissionDetailResponse> getSubmission(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId) {

        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

        return ResponseEntity.ok(
                submissionService.getSubmission(id, UUID.fromString(userId), isAdmin));
    }

    /**
     * GET /api/v1/submissions
     * List the current user's submissions (optionally filtered by problemId).
     */
    @GetMapping("/submissions")
    @Operation(summary = "List my submissions (optionally filtered by problemId)")
    public ResponseEntity<Page<SubmissionResponse>> mySubmissions(
            @RequestParam(required = false) UUID problemId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal String userId) {

        UUID userUUID = UUID.fromString(userId);
        Page<SubmissionResponse> page = (problemId != null)
                ? submissionService.getMySubmissionsForProblem(userUUID, problemId, pageable)
                : submissionService.getMySubmissions(userUUID, pageable);

        return ResponseEntity.ok(page);
    }
}
