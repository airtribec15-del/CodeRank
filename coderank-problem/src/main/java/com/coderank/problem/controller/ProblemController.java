package com.coderank.problem.controller;

import com.coderank.common.dto.response.SubmissionResponse;
import com.coderank.problem.client.SubmissionServiceClient;
import com.coderank.problem.client.SubmitCodeRequest;
import com.coderank.problem.dto.request.CreateProblemRequest;
import com.coderank.problem.dto.request.ProblemSubmitRequest;
import com.coderank.problem.dto.request.UpdateProblemRequest;
import com.coderank.problem.dto.response.ProblemDetailResponse;
import com.coderank.problem.dto.response.ProblemSummaryResponse;
import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import com.coderank.problem.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/problems")
@RequiredArgsConstructor
@Tag(name = "Problems", description = "Problem management and retrieval endpoints")
public class ProblemController {

    private final ProblemService problemService;
    private final SubmissionServiceClient submissionServiceClient;

    @GetMapping
    @Operation(summary = "List all published problems (paginated)")
    public ResponseEntity<Page<ProblemSummaryResponse>> listProblems(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(problemService.listPublishedProblems(pageable));
    }

    @GetMapping("/admin")
    @Operation(summary = "List all problems with filters (ADMIN only)")
    public ResponseEntity<Page<ProblemSummaryResponse>> listAllProblems(
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) ProblemState state,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(problemService.listAllProblems(difficulty, state, pageable));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get a problem by slug")
    public ResponseEntity<ProblemDetailResponse> getProblemBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(problemService.getProblemBySlug(slug));
    }

    @GetMapping("/id/{id}")
    @Operation(summary = "Get a problem by UUID (ADMIN only)")
    public ResponseEntity<ProblemDetailResponse> getProblemById(@PathVariable UUID id) {
        return ResponseEntity.ok(problemService.getProblemById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new problem (ADMIN only)")
    public ResponseEntity<ProblemDetailResponse> createProblem(
            @Valid @RequestBody CreateProblemRequest request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(problemService.createProblem(request, UUID.fromString(userId)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a problem (ADMIN only)")
    public ResponseEntity<ProblemDetailResponse> updateProblem(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProblemRequest request) {
        return ResponseEntity.ok(problemService.updateProblem(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a problem (ADMIN only)")
    public ResponseEntity<Void> deleteProblem(@PathVariable UUID id) {
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOCKED FLOW STEP 2-3: Client → Gateway → Problem Service → Submission Service
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Entry point for code submission in the locked flow.
     *
     * <p><strong>Flow:</strong>
     * <ol>
     *   <li>Client POSTs here via Gateway with JWT (validated by Gateway)</li>
     *   <li>Gateway injects X-User-Id + X-User-Role headers and routes here</li>
     *   <li>Problem Service verifies problem exists and is PUBLISHED</li>
     *   <li>Problem Service forwards to Submission Service via {@link SubmissionServiceClient}</li>
     *   <li>Returns 202 Accepted with jobId back to client</li>
     * </ol>
     * </p>
     *
     * @param problemId  path variable — the problem being submitted against
     * @param request    body — language + sourceCode
     * @param userId     injected by Gateway as X-User-Id (via GatewayAuthenticationFilter)
     * @param userRole   injected by Gateway as X-User-Role (forwarded to Submission Service)
     */
    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit code for a problem")
    public ResponseEntity<SubmissionResponse> submitCode(
            @PathVariable("id") UUID problemId,
            @Valid @RequestBody ProblemSubmitRequest request,
            @AuthenticationPrincipal String userId,
            @RequestHeader("X-User-Role") String userRole) {

        // Step 1: Verify the problem exists and is PUBLISHED (throws if not)
        problemService.verifyProblemPublished(problemId);

        // Step 2: Build the enriched payload with problemId for Submission Service
        SubmitCodeRequest submitRequest = SubmitCodeRequest.builder()
                .problemId(problemId)
                .language(request.getLanguage())
                .sourceCode(request.getSourceCode())
                .build();

        // Step 3: Forward to Submission Service; returns 202 Accepted with jobId
        SubmissionResponse response = submissionServiceClient.forwardSubmission(
                submitRequest,
                UUID.fromString(userId),
                userRole);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}