package com.coderank.problem.dto.request;

import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProblemRequest {

    @Size(max = 255)
    private String title;

    private String description;

    private Difficulty difficulty;

    private ProblemState state;

    private String constraints;

    private Set<UUID> topicIds;

    private Set<UUID> companyIds;
}