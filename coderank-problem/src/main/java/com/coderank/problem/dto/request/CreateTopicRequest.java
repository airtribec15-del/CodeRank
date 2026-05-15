package com.coderank.problem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTopicRequest {

    @NotBlank(message = "Topic name must not be blank")
    @Size(max = 100, message = "Topic name must be at most 100 characters")
    private String name;
}