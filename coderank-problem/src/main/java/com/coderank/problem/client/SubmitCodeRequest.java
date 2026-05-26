package com.coderank.problem.client;

import com.coderank.common.enums.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Payload Problem Service sends to Submission Service's
 * {@code POST /api/v1/submissions} endpoint (internal hop).
 *
 * <p>Contains the submission payload enriched with {@code problemId}
 * which is known by Problem Service from the path variable.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitCodeRequest {

    @NotNull(message = "Problem ID must not be null")
    private UUID problemId;

    @NotNull(message = "Language must not be null")
    private Language language;

    @NotBlank(message = "Source code must not be blank")
    @Size(max = 65536, message = "Source code must not exceed 64KB")
    private String sourceCode;
}