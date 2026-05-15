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
public class TestCaseRequest {

    @NotBlank(message = "Input must not be blank")
    private String input;

    @NotBlank(message = "Expected output must not be blank")
    private String expected;

    private boolean isSample;

    private int orderIndex;
}