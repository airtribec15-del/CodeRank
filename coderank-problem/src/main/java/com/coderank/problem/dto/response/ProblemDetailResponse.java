package com.coderank.problem.dto.response;

import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Full response for single problem view — includes examples (NOT test cases for regular users)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemDetailResponse {
    private UUID id;
    private String title;
    private String slug;
    private String description;
    private Difficulty difficulty;
    private ProblemState state;
    private String constraints;
    private Set<TopicResponse> topics;
    private Set<CompanyResponse> companies;
    private List<ProblemExampleResponse> examples;
    private Instant createdAt;
    private Instant updatedAt;
}