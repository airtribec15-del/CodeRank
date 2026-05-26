package com.coderank.problem.service;

import com.coderank.common.exception.InvalidRequestException;
import com.coderank.problem.dto.request.*;
import com.coderank.problem.dto.response.*;
import com.coderank.problem.entity.*;
import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import com.coderank.problem.mapper.ProblemMapper;
import com.coderank.problem.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemService")
class ProblemServiceTest {

    @Mock private ProblemRepository problemRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private ProblemMapper problemMapper;

    @InjectMocks private ProblemService problemService;

    private final UUID problemId = UUID.randomUUID();
    private final UUID createdBy = UUID.randomUUID();
    private Problem problem;
    private ProblemDetailResponse detailResponse;
    private ProblemSummaryResponse summaryResponse;

    @BeforeEach
    void setUp() {
        problem = Problem.builder()
                .id(problemId)
                .title("Two Sum")
                .slug("two-sum")
                .description("Given an array of integers")
                .difficulty(Difficulty.EASY)
                .state(ProblemState.DRAFT)
                .createdBy(createdBy)
                .topics(new HashSet<>())
                .companies(new HashSet<>())
                .examples(new ArrayList<>())
                .testCases(new ArrayList<>())
                .build();

        detailResponse = ProblemDetailResponse.builder()
                .id(problemId).title("Two Sum").slug("two-sum")
                .difficulty(Difficulty.EASY).state(ProblemState.DRAFT)
                .build();

        summaryResponse = ProblemSummaryResponse.builder()
                .id(problemId).title("Two Sum").slug("two-sum")
                .difficulty(Difficulty.EASY).state(ProblemState.DRAFT)
                .build();
    }

    // ------------------------------------------------------------------ //
    // createProblem                                                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("createProblem")
    class CreateProblem {

        private CreateProblemRequest minimalRequest() {
            return CreateProblemRequest.builder()
                    .title("Two Sum")
                    .description("d")
                    .difficulty(Difficulty.EASY)
                    .testCases(List.of(TestCaseRequest.builder()
                            .input("i").expected("o").isSample(true).orderIndex(0).build()))
                    .topicIds(new HashSet<>())
                    .companyIds(new HashSet<>())
                    .build();
        }

        @Test
        @DisplayName("returns detail response on valid request")
        void shouldReturnDetailResponse() {
            when(problemRepository.save(any(Problem.class))).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            ProblemDetailResponse result = problemService.createProblem(minimalRequest(), createdBy);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Two Sum");
        }

        @Test
        @DisplayName("new problem defaults to DRAFT state")
        void shouldDefaultToDraftState() {
            when(problemRepository.save(any(Problem.class))).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.createProblem(minimalRequest(), createdBy);

            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            verify(problemRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(ProblemState.DRAFT);
        }

        @Test
        @DisplayName("maps test cases onto the problem before saving")
        void shouldAttachTestCasesToProblem() {
            // FIX: stub save so that `saved` is non-null
            when(problemRepository.save(any(Problem.class))).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            TestCaseRequest tc = TestCaseRequest.builder()
                    .input("[2,7,9]").expected("[0,1]")
                    .isSample(true).orderIndex(0).build();

            CreateProblemRequest request = CreateProblemRequest.builder()
                    .title("Two Sum").description("d").difficulty(Difficulty.EASY)
                    .testCases(List.of(tc))
                    .topicIds(new HashSet<>())
                    .companyIds(new HashSet<>())
                    .build();

            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            problemService.createProblem(request, createdBy);
            verify(problemRepository).save(captor.capture());

            assertThat(captor.getValue().getTestCases()).hasSize(1);
            assertThat(captor.getValue().getTestCases().get(0).getInput())
                    .isEqualTo("[2,7,9]");
        }

        @Test
        @DisplayName("maps examples onto problem when examples are provided")
        void shouldAttachExamplesToProblem() {
            // FIX: stub save so that `saved` is non-null
            when(problemRepository.save(any(Problem.class))).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            ProblemExampleRequest ex = ProblemExampleRequest.builder()
                    .inputText("nums=[2,7]").outputText("[0,1]")
                    .explanation("indices 0 and 1").orderIndex(0).build();

            CreateProblemRequest request = CreateProblemRequest.builder()
                    .title("Two Sum").description("d").difficulty(Difficulty.EASY)
                    .testCases(List.of(TestCaseRequest.builder()
                            .input("i").expected("o").isSample(true).orderIndex(0).build()))
                    .examples(List.of(ex))
                    .topicIds(new HashSet<>())
                    .companyIds(new HashSet<>())
                    .build();

            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            problemService.createProblem(request, createdBy);
            verify(problemRepository).save(captor.capture());

            assertThat(captor.getValue().getExamples()).hasSize(1);
            assertThat(captor.getValue().getExamples().get(0).getInputText())
                    .isEqualTo("nums=[2,7]");
        }

        @Test
        @DisplayName("resolves topics when topicIds provided in request")
        void shouldResolveTopicsWhenProvided() {
            UUID topicId = UUID.randomUUID();
            Topic topic = Topic.builder().id(topicId).name("DP").build();
            Set<UUID> topicIds = Set.of(topicId);

            CreateProblemRequest request = CreateProblemRequest.builder()
                    .title("Two Sum").description("d").difficulty(Difficulty.EASY)
                    .testCases(List.of(TestCaseRequest.builder()
                            .input("i").expected("o").isSample(true).orderIndex(0).build()))
                    .topicIds(topicIds)
                    .companyIds(new HashSet<>())
                    .build();

            when(topicRepository.findAllByIdIn(topicIds)).thenReturn(Set.of(topic));
            when(problemRepository.save(any(Problem.class))).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.createProblem(request, createdBy);
            verify(topicRepository).findAllByIdIn(topicIds);
        }

        @Test
        @DisplayName("resolves companies when companyIds provided in request")
        void shouldResolveCompaniesWhenProvided() {
            UUID companyId = UUID.randomUUID();
            Company company = Company.builder().id(companyId).name("Google").build();
            Set<UUID> companyIds = Set.of(companyId);

            CreateProblemRequest request = CreateProblemRequest.builder()
                    .title("Two Sum").description("d").difficulty(Difficulty.EASY)
                    .testCases(List.of(TestCaseRequest.builder()
                            .input("i").expected("o").isSample(true).orderIndex(0).build()))
                    .topicIds(new HashSet<>())
                    .companyIds(companyIds)
                    .build();

            when(companyRepository.findAllByIdIn(companyIds)).thenReturn(Set.of(company));
            when(problemRepository.save(any(Problem.class))).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.createProblem(request, createdBy);
            verify(companyRepository).findAllByIdIn(companyIds);
        }

        @Test
        @DisplayName("sets createdBy on saved problem")
        void shouldSetCreatedBy() {
            when(problemRepository.save(any(Problem.class))).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.createProblem(minimalRequest(), createdBy);

            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            verify(problemRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(createdBy);
        }
    }

    // ------------------------------------------------------------------ //
    // listPublishedProblems                                                //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("listPublishedProblems")
    class ListPublishedProblems {

        @Test
        @DisplayName("calls repository with PUBLISHED state and returns mapped page")
        void shouldReturnPublishedProblemsPage() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Problem> page = new PageImpl<>(List.of(problem), pageable, 1);

            when(problemRepository.findAllByState(ProblemState.PUBLISHED, pageable)).thenReturn(page);
            when(problemMapper.toSummaryResponse(problem)).thenReturn(summaryResponse);

            Page<ProblemSummaryResponse> result = problemService.listPublishedProblems(pageable);

            assertThat(result).hasSize(1);
            assertThat(result.getContent().get(0).getTitle()).isEqualTo("Two Sum");
            verify(problemRepository).findAllByState(ProblemState.PUBLISHED, pageable);
        }

        @Test
        @DisplayName("returns empty page when no published problems exist")
        void shouldReturnEmptyPageWhenNonePublished() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Problem> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(problemRepository.findAllByState(ProblemState.PUBLISHED, pageable))
                    .thenReturn(emptyPage);

            Page<ProblemSummaryResponse> result = problemService.listPublishedProblems(pageable);
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------ //
    // updateProblem                                                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("updateProblem")
    class UpdateProblem {

        @Test
        @DisplayName("updates title when provided in request")
        void shouldUpdateTitleWhenProvided() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId, UpdateProblemRequest.builder().title("New Title").build());
            assertThat(problem.getTitle()).isEqualTo("New Title");
        }

        @Test
        @DisplayName("updates difficulty when provided in request")
        void shouldUpdateDifficultyWhenProvided() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId, UpdateProblemRequest.builder().difficulty(Difficulty.HARD).build());
            assertThat(problem.getDifficulty()).isEqualTo(Difficulty.HARD);
        }

        @Test
        @DisplayName("updates state when provided in request")
        void shouldUpdateStateWhenProvided() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId, UpdateProblemRequest.builder().state(ProblemState.PUBLISHED).build());
            assertThat(problem.getState()).isEqualTo(ProblemState.PUBLISHED);
        }

        @Test
        @DisplayName("updates constraints when provided in request")
        void shouldUpdateConstraintsWhenProvided() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId, UpdateProblemRequest.builder().constraints("1 ≤ n ≤ 10⁴").build());
            assertThat(problem.getConstraints()).isEqualTo("1 ≤ n ≤ 10⁴");
        }

        @Test
        @DisplayName("does not modify fields when all request fields are null")
        void shouldNotModifyFieldsWhenAllNull() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId, UpdateProblemRequest.builder().build());

            assertThat(problem.getTitle()).isEqualTo("Two Sum");
            assertThat(problem.getDifficulty()).isEqualTo(Difficulty.EASY);
            assertThat(problem.getState()).isEqualTo(ProblemState.DRAFT);
        }

        @Test
        @DisplayName("throws InvalidRequestException when problem not found")
        void shouldThrowWhenNotFound() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemService.updateProblem(problemId, UpdateProblemRequest.builder().title("x").build()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Problem not found");
        }

        @Test
        @DisplayName("resolves topics when topicIds provided in update")
        void shouldResolveTopicsOnUpdate() {
            UUID topicId = UUID.randomUUID();
            Topic topic = Topic.builder().id(topicId).name("DP").build();
            Set<UUID> topicIds = Set.of(topicId);

            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(topicRepository.findAllByIdIn(topicIds)).thenReturn(Set.of(topic));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId, UpdateProblemRequest.builder().topicIds(topicIds).build());
            assertThat(problem.getTopics()).containsExactly(topic);
        }

        @Test
        @DisplayName("resolves companies when companyIds provided in update")
        void shouldResolveCompaniesOnUpdate() {
            UUID companyId = UUID.randomUUID();
            Company company = Company.builder().id(companyId).name("Google").build();
            Set<UUID> companyIds = Set.of(companyId);

            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(companyRepository.findAllByIdIn(companyIds)).thenReturn(Set.of(company));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId, UpdateProblemRequest.builder().companyIds(companyIds).build());
            assertThat(problem.getCompanies()).containsExactly(company);
        }
    }

    // ------------------------------------------------------------------ //
    // updateProblemState                                                   //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("updateProblemState")
    class UpdateProblemState {

        @Test
        @DisplayName("sets state to PUBLISHED and saves")
        void shouldSetStateToPublished() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);

            problemService.updateProblemState(problemId, ProblemState.PUBLISHED);

            assertThat(problem.getState()).isEqualTo(ProblemState.PUBLISHED);
            verify(problemRepository).save(problem);
        }

        @Test
        @DisplayName("sets state to ARCHIVED and saves")
        void shouldSetStateToArchived() {
            problem.setState(ProblemState.PUBLISHED);
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);

            problemService.updateProblemState(problemId, ProblemState.ARCHIVED);

            assertThat(problem.getState()).isEqualTo(ProblemState.ARCHIVED);
        }

        @Test
        @DisplayName("throws when problem not found")
        void shouldThrowWhenNotFound() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemService.updateProblemState(problemId, ProblemState.PUBLISHED))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Problem not found");
        }
    }

    // ------------------------------------------------------------------ //
    // verifyProblemPublished                                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("verifyProblemPublished")
    class VerifyProblemPublished {

        @Test
        @DisplayName("does not throw when problem exists and is PUBLISHED")
        void shouldNotThrowWhenPublished() {
            problem.setState(ProblemState.PUBLISHED);
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));

            assertThatCode(() -> problemService.verifyProblemPublished(problemId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("throws when problem does not exist")
        void shouldThrowWhenNotFound() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemService.verifyProblemPublished(problemId))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("throws when problem exists but is DRAFT")
        void shouldThrowWhenDraft() {
            problem.setState(ProblemState.DRAFT);
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));

            assertThatThrownBy(() -> problemService.verifyProblemPublished(problemId))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("throws when problem exists but is ARCHIVED")
        void shouldThrowWhenArchived() {
            problem.setState(ProblemState.ARCHIVED);
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));

            assertThatThrownBy(() -> problemService.verifyProblemPublished(problemId))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    // ------------------------------------------------------------------ //
    // getTestCasesForExecution                                             //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("getTestCasesForExecution")
    class GetTestCasesForExecution {

        @Test
        @DisplayName("returns mapped test cases for a valid problem id")
        void shouldReturnMappedTestCases() {
            TestCase tc = TestCase.builder()
                    .id(UUID.randomUUID()).input("[2,7,9]").expected("[0,1]")
                    .isSample(true).orderIndex(0).build();
            InternalTestCaseResponse tcResponse = InternalTestCaseResponse.builder()
                    .input("[2,7,9]").expected("[0,1]").isSample(true).orderIndex(0).build();

            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(testCaseRepository.findAllByProblemIdOrderByOrderIndexAsc(problemId))
                    .thenReturn(List.of(tc));
            when(problemMapper.toInternalTestCaseResponse(tc)).thenReturn(tcResponse);

            List<InternalTestCaseResponse> result = problemService.getTestCasesForExecution(problemId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getInput()).isEqualTo("[2,7,9]");
        }

        @Test
        @DisplayName("returns empty list when problem has no test cases")
        void shouldReturnEmptyListWhenNoTestCases() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(testCaseRepository.findAllByProblemIdOrderByOrderIndexAsc(problemId))
                    .thenReturn(List.of());

            List<InternalTestCaseResponse> result = problemService.getTestCasesForExecution(problemId);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("throws when problem does not exist")
        void shouldThrowWhenProblemNotFound() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemService.getTestCasesForExecution(problemId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Problem not found");
        }

        @Test
        @DisplayName("maps multiple test cases in correct order")
        void shouldMapMultipleTestCases() {
            TestCase tc1 = TestCase.builder().id(UUID.randomUUID())
                    .input("1").expected("1").isSample(true).orderIndex(0).build();
            TestCase tc2 = TestCase.builder().id(UUID.randomUUID())
                    .input("2").expected("2").isSample(false).orderIndex(1).build();
            InternalTestCaseResponse r1 = InternalTestCaseResponse.builder()
                    .input("1").expected("1").isSample(true).orderIndex(0).build();
            InternalTestCaseResponse r2 = InternalTestCaseResponse.builder()
                    .input("2").expected("2").isSample(false).orderIndex(1).build();

            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(testCaseRepository.findAllByProblemIdOrderByOrderIndexAsc(problemId))
                    .thenReturn(List.of(tc1, tc2));
            when(problemMapper.toInternalTestCaseResponse(tc1)).thenReturn(r1);
            when(problemMapper.toInternalTestCaseResponse(tc2)).thenReturn(r2);

            List<InternalTestCaseResponse> result = problemService.getTestCasesForExecution(problemId);
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getOrderIndex()).isEqualTo(0);
            assertThat(result.get(1).getOrderIndex()).isEqualTo(1);
        }
    }
}