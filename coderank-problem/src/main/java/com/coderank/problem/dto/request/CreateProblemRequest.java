package com.coderank.problem.dto.request;

import com.coderank.problem.enums.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProblemRequest {

    @NotBlank(message = "Title must not be blank")
    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    @NotBlank(message = "Description must not be blank")
    private String description;

    @NotNull(message = "Difficulty must not be null")
    private Difficulty difficulty;

    private String constraints;

    // IDs of existing topics to associate
    private Set<UUID> topicIds;

    // IDs of existing companies to associate
    private Set<UUID> companyIds;

    @Valid
    private List<ProblemExampleRequest> examples;

    @Valid
    @NotEmpty(message = "At least one test case is required")
    private List<TestCaseRequest> testCases;
}