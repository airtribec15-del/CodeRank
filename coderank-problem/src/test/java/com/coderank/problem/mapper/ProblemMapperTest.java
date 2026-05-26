package com.coderank.problem.mapper;

import com.coderank.problem.dto.response.*;
import com.coderank.problem.entity.*;
import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProblemMapper")
class ProblemMapperTest {

    private final ProblemMapper mapper = new ProblemMapper();

    private final UUID topicId   = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID problemId = UUID.randomUUID();
    private final UUID exampleId = UUID.randomUUID();
    private final UUID tcId      = UUID.randomUUID();

    // ------------------------------------------------------------------ //
    //  toTopicResponse                                                     //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toTopicResponse")
    class ToTopicResponse {

        @Test
        @DisplayName("maps id and name correctly")
        void shouldMapIdAndName() {
            Topic topic = Topic.builder().id(topicId).name("Arrays").build();

            TopicResponse result = mapper.toTopicResponse(topic);

            assertThat(result.getId()).isEqualTo(topicId);
            assertThat(result.getName()).isEqualTo("Arrays");
        }
    }

    // ------------------------------------------------------------------ //
    //  toCompanyResponse                                                   //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toCompanyResponse")
    class ToCompanyResponse {

        @Test
        @DisplayName("maps id and name correctly")
        void shouldMapIdAndName() {
            Company company = Company.builder().id(companyId).name("Meta").build();

            CompanyResponse result = mapper.toCompanyResponse(company);

            assertThat(result.getId()).isEqualTo(companyId);
            assertThat(result.getName()).isEqualTo("Meta");
        }
    }

    // ------------------------------------------------------------------ //
    //  toExampleResponse                                                   //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toExampleResponse")
    class ToExampleResponse {

        @Test
        @DisplayName("maps all fields including optional explanation")
        void shouldMapAllFields() {
            Topic topic = Topic.builder().id(topicId).name("DP").build();
            Problem problem = Problem.builder().id(problemId).title("T")
                    .slug("t").description("d").difficulty(Difficulty.EASY)
                    .state(ProblemState.DRAFT).createdBy(UUID.randomUUID())
                    .topics(Set.of(topic)).companies(new HashSet<>())
                    .examples(new ArrayList<>()).testCases(new ArrayList<>()).build();

            ProblemExample example = ProblemExample.builder()
                    .id(exampleId).problem(problem)
                    .inputText("nums=[2,7]").outputText("[0,1]")
                    .explanation("indices 0 and 1").orderIndex(1).build();

            ProblemExampleResponse result = mapper.toExampleResponse(example);

            assertThat(result.getId()).isEqualTo(exampleId);
            assertThat(result.getInputText()).isEqualTo("nums=[2,7]");
            assertThat(result.getOutputText()).isEqualTo("[0,1]");
            assertThat(result.getExplanation()).isEqualTo("indices 0 and 1");
            assertThat(result.getOrderIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("maps null explanation as null")
        void shouldMapNullExplanation() {
            Problem problem = Problem.builder().id(problemId).title("T")
                    .slug("t").description("d").difficulty(Difficulty.EASY)
                    .state(ProblemState.DRAFT).createdBy(UUID.randomUUID())
                    .topics(new HashSet<>()).companies(new HashSet<>())
                    .examples(new ArrayList<>()).testCases(new ArrayList<>()).build();

            ProblemExample example = ProblemExample.builder()
                    .id(exampleId).problem(problem)
                    .inputText("a").outputText("b")
                    .explanation(null).orderIndex(0).build();

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
        @DisplayName("maps all test case fields correctly")
        void shouldMapAllFields() {
            Problem problem = Problem.builder().id(problemId).title("T")
                    .slug("t").description("d").difficulty(Difficulty.EASY)
                    .state(ProblemState.DRAFT).createdBy(UUID.randomUUID())
                    .topics(new HashSet<>()).companies(new HashSet<>())
                    .examples(new ArrayList<>()).testCases(new ArrayList<>()).build();

            TestCase tc = TestCase.builder()
                    .id(tcId).problem(problem)
                    .input("[2,7]\n9").expected("[0,1]")
                    .isSample(true).orderIndex(0).build();

            InternalTestCaseResponse result = mapper.toInternalTestCaseResponse(tc);

            assertThat(result.getId()).isEqualTo(tcId);
            assertThat(result.getInput()).isEqualTo("[2,7]\n9");
            assertThat(result.getExpected()).isEqualTo("[0,1]");
            assertThat(result.isSample()).isTrue();
            assertThat(result.getOrderIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("maps non-sample test case correctly")
        void shouldMapNonSampleTestCase() {
            Problem problem = Problem.builder().id(problemId).title("T")
                    .slug("t").description("d").difficulty(Difficulty.EASY)
                    .state(ProblemState.DRAFT).createdBy(UUID.randomUUID())
                    .topics(new HashSet<>()).companies(new HashSet<>())
                    .examples(new ArrayList<>()).testCases(new ArrayList<>()).build();

            TestCase tc = TestCase.builder()
                    .id(tcId).problem(problem)
                    .input("hidden").expected("result")
                    .isSample(false).orderIndex(3).build();

            InternalTestCaseResponse result = mapper.toInternalTestCaseResponse(tc);

            assertThat(result.isSample()).isFalse();
            assertThat(result.getOrderIndex()).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------ //
    //  toSummaryResponse                                                   //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("toSummaryResponse")
    class ToSummaryResponse {

        @Test
        @DisplayName("maps all summary fields correctly")
        void shouldMapAllSummaryFields() {
            Topic topic = Topic.builder().id(topicId).name("DP").build();
            Company company = Company.builder().id(companyId).name("Google").build();
            Instant now = Instant.now();

            Problem problem = Problem.builder()
                    .id(problemId).title("Two Sum").slug("two-sum")
                    .description("desc").difficulty(Difficulty.EASY)
                    .state(ProblemState.PUBLISHED)
                    .createdBy(UUID.randomUUID())
                    .topics(Set.of(topic))
                    .companies(Set.of(company))
                    .examples(new ArrayList<>())
                    .testCases(new ArrayList<>())
                    .build();

            ProblemSummaryResponse result = mapper.toSummaryResponse(problem);

            assertThat(result.getId()).isEqualTo(problemId);
            assertThat(result.getTitle()).isEqualTo("Two Sum");
            assertThat(result.getSlug()).isEqualTo("two-sum");
            assertThat(result.getDifficulty()).isEqualTo(Difficulty.EASY);
            assertThat(result.getState()).isEqualTo(ProblemState.PUBLISHED);
            assertThat(result.getTopics()).hasSize(1);
            assertThat(result.getCompanies()).hasSize(1);
        }

        @Test
        @DisplayName("returns empty sets when problem has no topics or companies")
        void shouldReturnEmptySetsWhenNone() {
            Problem problem = Problem.builder()
                    .id(problemId).title("T").slug("t")
                    .description("d").difficulty(Difficulty.EASY)
                    .state(ProblemState.DRAFT)
                    .createdBy(UUID.randomUUID())
                    .topics(new HashSet<>())
                    .companies(new HashSet<>())
                    .examples(new ArrayList<>())
                    .testCases(new ArrayList<>())
                    .build();

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
        @DisplayName("maps all detail fields including examples")
        void shouldMapAllDetailFields() {
            Topic topic = Topic.builder().id(topicId).name("DP").build();

            Problem problem = Problem.builder()
                    .id(problemId).title("Two Sum").slug("two-sum")
                    .description("find two nums").difficulty(Difficulty.MEDIUM)
                    .state(ProblemState.PUBLISHED).constraints("1 <= n <= 10^4")
                    .createdBy(UUID.randomUUID())
                    .topics(Set.of(topic))
                    .companies(new HashSet<>())
                    .examples(new ArrayList<>())
                    .testCases(new ArrayList<>())
                    .build();

            ProblemDetailResponse result = mapper.toDetailResponse(problem);

            assertThat(result.getId()).isEqualTo(problemId);
            assertThat(result.getTitle()).isEqualTo("Two Sum");
            assertThat(result.getDescription()).isEqualTo("find two nums");
            assertThat(result.getDifficulty()).isEqualTo(Difficulty.MEDIUM);
            assertThat(result.getState()).isEqualTo(ProblemState.PUBLISHED);
            assertThat(result.getConstraints()).isEqualTo("1 <= n <= 10^4");
            assertThat(result.getTopics()).hasSize(1);
            assertThat(result.getExamples()).isEmpty();
        }

        @Test
        @DisplayName("maps examples list in detail response")
        void shouldMapExamplesInDetailResponse() {
            Problem problem = Problem.builder()
                    .id(problemId).title("T").slug("t")
                    .description("d").difficulty(Difficulty.EASY)
                    .state(ProblemState.DRAFT).createdBy(UUID.randomUUID())
                    .topics(new HashSet<>()).companies(new HashSet<>())
                    .testCases(new ArrayList<>())
                    .examples(new ArrayList<>())
                    .build();

            ProblemExample example = ProblemExample.builder()
                    .id(exampleId).problem(problem)
                    .inputText("in").outputText("out")
                    .explanation(null).orderIndex(0).build();
            problem.getExamples().add(example);

            ProblemDetailResponse result = mapper.toDetailResponse(problem);

            assertThat(result.getExamples()).hasSize(1);
            assertThat(result.getExamples().get(0).getInputText()).isEqualTo("in");
        }
    }
}