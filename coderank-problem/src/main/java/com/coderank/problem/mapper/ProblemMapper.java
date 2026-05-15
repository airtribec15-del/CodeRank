package com.coderank.problem.mapper;

import com.coderank.problem.dto.response.*;
import com.coderank.problem.entity.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProblemMapper {

    public TopicResponse toTopicResponse(Topic topic) {
        return TopicResponse.builder()
                .id(topic.getId())
                .name(topic.getName())
                .build();
    }

    public CompanyResponse toCompanyResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .build();
    }

    public ProblemExampleResponse toExampleResponse(ProblemExample example) {
        return ProblemExampleResponse.builder()
                .id(example.getId())
                .inputText(example.getInputText())
                .outputText(example.getOutputText())
                .explanation(example.getExplanation())
                .orderIndex(example.getOrderIndex())
                .build();
    }

    public InternalTestCaseResponse toInternalTestCaseResponse(TestCase testCase) {
        return InternalTestCaseResponse.builder()
                .id(testCase.getId())
                .input(testCase.getInput())
                .expected(testCase.getExpected())
                .isSample(testCase.isSample())
                .orderIndex(testCase.getOrderIndex())
                .build();
    }

    public ProblemSummaryResponse toSummaryResponse(Problem problem) {
        Set<TopicResponse> topics = problem.getTopics().stream()
                .map(this::toTopicResponse)
                .collect(Collectors.toSet());

        Set<CompanyResponse> companies = problem.getCompanies().stream()
                .map(this::toCompanyResponse)
                .collect(Collectors.toSet());

        return ProblemSummaryResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .slug(problem.getSlug())
                .difficulty(problem.getDifficulty())
                .state(problem.getState())
                .topics(topics)
                .companies(companies)
                .createdAt(problem.getCreatedAt())
                .build();
    }

    public ProblemDetailResponse toDetailResponse(Problem problem) {
        Set<TopicResponse> topics = problem.getTopics().stream()
                .map(this::toTopicResponse)
                .collect(Collectors.toSet());

        Set<CompanyResponse> companies = problem.getCompanies().stream()
                .map(this::toCompanyResponse)
                .collect(Collectors.toSet());

        List<ProblemExampleResponse> examples = problem.getExamples().stream()
                .map(this::toExampleResponse)
                .toList();

        return ProblemDetailResponse.builder()
                .id(problem.getId())
                .title(problem.getTitle())
                .slug(problem.getSlug())
                .description(problem.getDescription())
                .difficulty(problem.getDifficulty())
                .state(problem.getState())
                .constraints(problem.getConstraints())
                .topics(topics)
                .companies(companies)
                .examples(examples)
                .createdAt(problem.getCreatedAt())
                .updatedAt(problem.getUpdatedAt())
                .build();
    }
}