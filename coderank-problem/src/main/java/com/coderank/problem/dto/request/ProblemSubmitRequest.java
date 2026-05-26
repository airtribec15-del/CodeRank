package com.coderank.problem.dto.request;

import com.coderank.common.enums.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/problems/{id}/submit}.
 * Received from the client via the API Gateway.
 * Problem Service enriches this with problemId before forwarding to Submission Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSubmitRequest {

    @NotNull(message = "Language must not be null")
    private Language language;

    @NotBlank(message = "Source code must not be blank")
    @Size(max = 65536, message = "Source code must not exceed 64KB")
    private String sourceCode;
}