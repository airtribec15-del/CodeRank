package com.coderank.problem.controller;

import com.coderank.problem.dto.response.InternalTestCaseResponse;
import com.coderank.problem.enums.ProblemState;
import com.coderank.problem.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Internal endpoints — consumed only by Execution Service and Result Processor.
 * These are BLOCKED at the API Gateway for all external traffic.
 * Path prefix: /api/v1/internal/problems/
 */
@RestController
@RequestMapping("/api/v1/internal/problems")
@RequiredArgsConstructor
@Tag(name = "Internal - Problems", description = "Internal endpoints for service-to-service communication only")
public class InternalProblemController {

    private final ProblemService problemService;

    @GetMapping("/{id}/testcases")
    @Operation(summary = "Fetch all test cases for a problem (Execution Service only)")
    public ResponseEntity<List<InternalTestCaseResponse>> getTestCases(@PathVariable UUID id) {
        return ResponseEntity.ok(problemService.getTestCasesForExecution(id));
    }

    @PatchMapping("/state")
    @Operation(summary = "Update problem state (Result Processor only)")
    public ResponseEntity<Void> updateState(
            @RequestParam UUID problemId,
            @RequestParam ProblemState state) {
        problemService.updateProblemState(problemId, state);
        return ResponseEntity.noContent().build();
    }
}