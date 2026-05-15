package com.coderank.problem.dto.response;

import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

// Lightweight response for paginated problem lists — no test cases, no examples
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSummaryResponse {
    private UUID id;
    private String title;
    private String slug;
    private Difficulty difficulty;
    private ProblemState state;
    private Set<TopicResponse> topics;
    private Set<CompanyResponse> companies;
    private Instant createdAt;
}