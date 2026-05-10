package com.coderank.common.dto.request;

import com.coderank.common.enums.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteRequest {

    @NotNull(message = "Language must not be null")
    private Language language;

    @NotBlank(message = "Source code must not be blank")
    @Size(max = 65536, message = "Source code must not exceed 64KB")
    private String sourceCode;

    @Size(max = 8192, message = "Stdin input must not exceed 8KB")
    private String stdinInput;
}