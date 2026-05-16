package com.coderank.problem.mapper;

import com.coderank.problem.dto.response.*;
import com.coderank.problem.entity.*;
import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProblemMapper")
class ProblemMapperTest {

    private ProblemMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProblemMapper();
    }

    // ------------------------------------------------------------------ //
    //  toTopicResponse                                                     //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toTopicResponse")
    class ToTopicResponse {

        @Test
        @DisplayName("maps id and name from Topic entity")
        void shouldMapTopicIdAndName() {
            UUID id = UUID.randomUUID();
            Topic topic = Topic.builder().id(id).name("Array").build();

            TopicResponse result = mapper.toTopicResponse(topic);

            assertThat(result.getId()).isEqualTo(id);
            assertThat(result.getName()).isEqualTo("Array");
        }
    }

    // ------------------------------------------------------------------ //
    //  toCompanyResponse                                                   //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toCompanyResponse")
    class ToCompanyResponse {

        @Test
        @DisplayName("maps id and name from Company entity")
        void shouldMapCompanyIdAndName() {
            UUID id = UUID.randomUUID();
            Company company = Company.builder().id(id).name("Google").build();

            CompanyResponse result = mapper.toCompanyResponse(company);

            assertThat(result.getId()).isEqualTo(id);
            assertThat(result.getName()).isEqualTo("Google");
        }
    }

    // ------------------------------------------------------------------ //
    //  toExampleResponse                                                   //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toExampleResponse")
    class ToExampleResponse {

        @Test
        @DisplayName("maps all fields from ProblemExample entity")
        void shouldMapAllExampleFields() {
            UUID id = UUID.randomUUID();
            ProblemExample example = ProblemExample.builder()
                    .id(id)
                    .inputText("nums = [2,7,11,15], target = 9")
                    .outputText("[0,1]")
                    .explanation("nums[0] + nums[1] == 9")
                    .orderIndex(0)
                    .build();

            ProblemExampleResponse result = mapper.toExampleResponse(example);

            assertThat(result.getId()).isEqualTo(id);
            assertThat(result.getInputText()).isEqualTo("nums = [2,7,11,15], target = 9");
            assertThat(result.getOutputText()).isEqualTo("[0,1]");
            assertThat(result.getExplanation()).isEqualTo("nums[0] + nums[1] == 9");
            assertThat(result.getOrderIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("maps null explanation without error")
        void shouldHandleNullExplanation() {
            ProblemExample example = ProblemExample.builder()
                    .id(UUID.randomUUID())
                    .inputText("in").outputText("out")
                    .explanation(null).orderIndex(1)
                    .build();

            ProblemExampleResponse result = mapper.toExampleResponse(example);

            assertThat(result.getExplanation()).isNull();
        }
    }

    // ------------------------------------------------------------------ //
    //  toInternalTestCaseResponse                                          //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toInternalTestCaseResponse")
    class ToInternalTestCaseResponse {

        @Test
        @DisplayName("maps all fields from TestCase entity")
        void shouldMapAllTestCaseFields() {
            UUID id = UUID.randomUUID();
            TestCase tc = TestCase.builder()
                    .id(id).input("[2,7]\n9").expected("[0,1]")
                    .isSample(true).orderIndex(0).build();

            InternalTestCaseResponse result = mapper.toInternalTestCaseResponse(tc);

            assertThat(result.getId()).isEqualTo(id);
            assertThat(result.getInput()).isEqualTo("[2,7]\n9");
            assertThat(result.getExpected()).isEqualTo("[0,1]");
            assertThat(result.isSample()).isTrue();
            assertThat(result.getOrderIndex()).isEqualTo(0);
        }
    }

    // ------------------------------------------------------------------ //
    //  toSummaryResponse                                                   //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toSummaryResponse")
    class ToSummaryResponse {

        @Test
        @DisplayName("maps all summary fields including topics and companies")
        void shouldMapAllSummaryFields() {
            UUID topicId   = UUID.randomUUID();
            UUID companyId = UUID.randomUUID();
            Instant now    = Instant.now();

            Topic   topic   = Topic.builder().id(topicId).name("Array").build();
            Company company = Company.builder().id(companyId).name("Google").build();

            Problem problem = Problem.builder()
                    .id(UUID.randomUUID())
                    .title("Two Sum").slug("two-sum")
                    .difficulty(Difficulty.EASY).state(ProblemState.PUBLISHED)
                    .topics(Set.of(topic)).companies(Set.of(company))
                    .examples(new ArrayList<>()).testCases(new ArrayList<>())
                    .createdAt(now).build();

            ProblemSummaryResponse result = mapper.toSummaryResponse(problem);

            assertThat(result.getTitle()).isEqualTo("Two Sum");
            assertThat(result.getSlug()).isEqualTo("two-sum");
            assertThat(result.getDifficulty()).isEqualTo(Difficulty.EASY);
            assertThat(result.getState()).isEqualTo(ProblemState.PUBLISHED);
            assertThat(result.getTopics()).hasSize(1);
            assertThat(result.getCompanies()).hasSize(1);
            assertThat(result.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("maps empty topic and company sets without error")
        void shouldHandleEmptyCollections() {
            Problem problem = Problem.builder()
                    .id(UUID.randomUUID())
                    .title("T").slug("t")
                    .difficulty(Difficulty.EASY).state(ProblemState.DRAFT)
                    .topics(new HashSet<>()).companies(new HashSet<>())
                    .examples(new ArrayList<>()).testCases(new ArrayList<>())
                    .createdAt(Instant.now()).build();

            ProblemSummaryResponse result = mapper.toSummaryResponse(problem);

            assertThat(result.getTopics()).isEmpty();
            assertThat(result.getCompanies()).isEmpty();
        }
    }

    // ------------------------------------------------------------------ //
    //  toDetailResponse                                                    //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toDetailResponse")
    class ToDetailResponse {

        @Test
        @DisplayName("maps all detail fields including examples, constraints, and timestamps")
        void shouldMapAllDetailFields() {
            Instant created = Instant.parse("2025-01-01T00:00:00Z");
            Instant updated = Instant.parse("2025-06-01T00:00:00Z");

            ProblemExample example = ProblemExample.builder()
                    .id(UUID.randomUUID())
                    .inputText("in").outputText("out")
                    .explanation(null).orderIndex(0).build();

            Problem problem = Problem.builder()
                    .id(UUID.randomUUID())
                    .title("Two Sum").slug("two-sum")
                    .description("Find two numbers.").difficulty(Difficulty.EASY)
                    .state(ProblemState.PUBLISHED).constraints("1 <= n <= 10^4")
                    .topics(new HashSet<>()).companies(new HashSet<>())
                    .examples(List.of(example)).testCases(new ArrayList<>())
                    .createdAt(created).updatedAt(updated).build();

            ProblemDetailResponse result = mapper.toDetailResponse(problem);

            assertThat(result.getDescription()).isEqualTo("Find two numbers.");
            assertThat(result.getConstraints()).isEqualTo("1 <= n <= 10^4");
            assertThat(result.getExamples()).hasSize(1);
            assertThat(result.getCreatedAt()).isEqualTo(created);
            assertThat(result.getUpdatedAt()).isEqualTo(updated);
        }

        @Test
        @DisplayName("maps null constraints without error")
        void shouldHandleNullConstraints() {
            Problem problem = Problem.builder()
                    .id(UUID.randomUUID())
                    .title("T").slug("t").description("d")
                    .difficulty(Difficulty.EASY).state(ProblemState.DRAFT)
                    .constraints(null)
                    .topics(new HashSet<>()).companies(new HashSet<>())
                    .examples(new ArrayList<>()).testCases(new ArrayList<>())
                    .createdAt(Instant.now()).updatedAt(Instant.now()).build();

            ProblemDetailResponse result = mapper.toDetailResponse(problem);

            assertThat(result.getConstraints()).isNull();
        }
    }
}