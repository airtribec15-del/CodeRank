package com.coderank.submission.dto.request;

import com.coderank.common.enums.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RunRequest {

    @NotNull(message = "Language must not be null")
    private Language language;

    @NotBlank(message = "Source code must not be blank")
    @Size(max = 65536, message = "Source code must not exceed 64 KB")
    private String sourceCode;

    @Size(max = 8192, message = "Stdin input must not exceed 8 KB")
    private String stdinInput;
}
