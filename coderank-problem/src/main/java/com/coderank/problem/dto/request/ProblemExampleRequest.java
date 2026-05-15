package com.coderank.problem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemExampleRequest {

    @NotBlank(message = "Input text must not be blank")
    private String inputText;

    @NotBlank(message = "Output text must not be blank")
    private String outputText;

    private String explanation;

    private int orderIndex;
}