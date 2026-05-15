package com.coderank.problem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Internal-only DTO — returned by /api/v1/internal/problems/{id}/testcases
// Consumed by Execution Service. Never exposed through Gateway.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalTestCaseResponse {
    private UUID id;
    private String input;
    private String expected;
    private boolean isSample;
    private int orderIndex;
}