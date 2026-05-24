package com.coderank.submission.dto.request;

import com.coderank.common.enums.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SubmitRequest {

    @NotNull(message = "Problem ID must not be null")
    private UUID problemId;

    @NotNull(message = "Language must not be null")
    private Language language;

    @NotBlank(message = "Source code must not be blank")
    @Size(max = 65536, message = "Source code must not exceed 64 KB")
    private String sourceCode;
}
