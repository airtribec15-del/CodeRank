package com.coderank.problem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemExampleResponse {
    private UUID id;
    private String inputText;
    private String outputText;
    private String explanation;
    private int orderIndex;
}