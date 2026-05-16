package com.coderank.problem.service;

import com.coderank.common.exception.InvalidRequestException;
import com.coderank.problem.dto.request.CreateProblemRequest;
import com.coderank.problem.dto.request.UpdateProblemRequest;
import com.coderank.problem.dto.request.TestCaseRequest;
import com.coderank.problem.dto.response.InternalTestCaseResponse;
import com.coderank.problem.dto.response.ProblemDetailResponse;
import com.coderank.problem.dto.response.ProblemSummaryResponse;
import com.coderank.problem.entity.Company;
import com.coderank.problem.entity.Problem;
import com.coderank.problem.entity.TestCase;
import com.coderank.problem.entity.Topic;
import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import com.coderank.problem.mapper.ProblemMapper;
import com.coderank.problem.repository.CompanyRepository;
import com.coderank.problem.repository.ProblemRepository;
import com.coderank.problem.repository.TestCaseRepository;
import com.coderank.problem.repository.TopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private UUID problemId;
    private UUID createdBy;
    private Problem problem;
    private ProblemDetailResponse detailResponse;
    private ProblemSummaryResponse summaryResponse;

    @BeforeEach
    void setUp() {
        problemId = UUID.randomUUID();
        createdBy = UUID.randomUUID();

        problem = Problem.builder()
                .id(problemId)
                .title("Two Sum")
                .slug("two-sum")
                .description("Find two numbers that add to target.")
                .difficulty(Difficulty.EASY)
                .state(ProblemState.DRAFT)
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .topics(new HashSet<>())
                .companies(new HashSet<>())
                .examples(new ArrayList<>())
                .testCases(new ArrayList<>())
                .build();

        detailResponse = ProblemDetailResponse.builder()
                .id(problemId)
                .title("Two Sum")
                .slug("two-sum")
                .difficulty(Difficulty.EASY)
                .state(ProblemState.DRAFT)
                .build();

        summaryResponse = ProblemSummaryResponse.builder()
                .id(problemId)
                .title("Two Sum")
                .slug("two-sum")
                .difficulty(Difficulty.EASY)
                .state(ProblemState.PUBLISHED)
                .build();
    }

    // ------------------------------------------------------------------ //
    //  listPublishedProblems                                               //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("listPublishedProblems")
    class ListPublishedProblems {

        @Test
        @DisplayName("returns page of published problems mapped to summary responses")
        void shouldReturnPublishedProblemsPage() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Problem> page = new PageImpl<>(List.of(problem), pageable, 1);

            when(problemRepository.findAllByState(ProblemState.PUBLISHED, pageable))
                    .thenReturn(page);
            when(problemMapper.toSummaryResponse(problem)).thenReturn(summaryResponse);

            Page<ProblemSummaryResponse> result =
                    problemService.listPublishedProblems(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getSlug()).isEqualTo("two-sum");
            verify(problemRepository).findAllByState(ProblemState.PUBLISHED, pageable);
        }

        @Test
        @DisplayName("returns empty page when no published problems exist")
        void shouldReturnEmptyPageWhenNonePublished() {
            Pageable pageable = PageRequest.of(0, 20);
            when(problemRepository.findAllByState(ProblemState.PUBLISHED, pageable))
                    .thenReturn(Page.empty(pageable));

            Page<ProblemSummaryResponse> result =
                    problemService.listPublishedProblems(pageable);

            assertThat(result.isEmpty()).isTrue();
        }
    }

    // ------------------------------------------------------------------ //
    //  listAllProblems                                                     //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("listAllProblems")
    class ListAllProblems {

        @Test
        @DisplayName("passes difficulty and state filters to repository")
        void shouldPassFiltersToRepository() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Problem> page = new PageImpl<>(List.of(problem), pageable, 1);

            when(problemRepository.findAllByFilters(Difficulty.EASY, ProblemState.DRAFT, pageable))
                    .thenReturn(page);
            when(problemMapper.toSummaryResponse(problem)).thenReturn(summaryResponse);

            Page<ProblemSummaryResponse> result =
                    problemService.listAllProblems(Difficulty.EASY, ProblemState.DRAFT, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(problemRepository).findAllByFilters(Difficulty.EASY, ProblemState.DRAFT, pageable);
        }

        @Test
        @DisplayName("passes null filters when not provided")
        void shouldPassNullFiltersThrough() {
            Pageable pageable = PageRequest.of(0, 20);
            when(problemRepository.findAllByFilters(null, null, pageable))
                    .thenReturn(Page.empty(pageable));

            problemService.listAllProblems(null, null, pageable);

            verify(problemRepository).findAllByFilters(null, null, pageable);
        }
    }

    // ------------------------------------------------------------------ //
    //  getProblemBySlug                                                    //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("getProblemBySlug")
    class GetProblemBySlug {

        @Test
        @DisplayName("returns detail response for a known slug")
        void shouldReturnDetailResponseForKnownSlug() {
            when(problemRepository.findBySlug("two-sum"))
                    .thenReturn(Optional.of(problem));
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            ProblemDetailResponse result = problemService.getProblemBySlug("two-sum");

            assertThat(result.getSlug()).isEqualTo("two-sum");
        }

        @Test
        @DisplayName("throws InvalidRequestException containing the slug when not found")
        void shouldThrowWithSlugInMessageWhenNotFound() {
            when(problemRepository.findBySlug("unknown"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemService.getProblemBySlug("unknown"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("unknown");
        }
    }

    // ------------------------------------------------------------------ //
    //  getProblemById                                                      //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("getProblemById")
    class GetProblemById {

        @Test
        @DisplayName("returns detail response for a known id")
        void shouldReturnDetailResponseForKnownId() {
            when(problemRepository.findById(problemId))
                    .thenReturn(Optional.of(problem));
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            ProblemDetailResponse result = problemService.getProblemById(problemId);

            assertThat(result.getId()).isEqualTo(problemId);
        }

        @Test
        @DisplayName("throws InvalidRequestException when not found")
        void shouldThrowWhenIdNotFound() {
            UUID missing = UUID.randomUUID();
            when(problemRepository.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemService.getProblemById(missing))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Problem not found");
        }
    }

    // ------------------------------------------------------------------ //
    //  createProblem                                                       //
    // ------------------------------------------------------------------ //
    // LENIENT on this nested class because createProblem conditionally skips
    // topicRepository/companyRepository calls when the ID sets are empty,
    // and different tests need different subsets of stubs.
    @Nested
    @DisplayName("createProblem")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class CreateProblem {

        @BeforeEach
        void stubAlways() {
            // These are called unconditionally on every createProblem path
            when(problemRepository.existsBySlug(anyString())).thenReturn(false);
            when(problemRepository.save(any(Problem.class))).thenReturn(problem);
        }

        private CreateProblemRequest buildRequest(String title) {
            TestCaseRequest tc = TestCaseRequest.builder()
                    .input("[2,7,11,15]\n9").expected("[0,1]")
                    .isSample(true).orderIndex(0).build();
            return CreateProblemRequest.builder()
                    .title(title)
                    .description("Find two numbers.")
                    .difficulty(Difficulty.EASY)
                    .testCases(List.of(tc))
                    .topicIds(new HashSet<>())
                    .companyIds(new HashSet<>())
                    .build();
        }

        @Test
        @DisplayName("saves problem and returns mapped detail response")
        void shouldSaveAndReturnDetailResponse() {
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            ProblemDetailResponse result =
                    problemService.createProblem(buildRequest("Two Sum"), createdBy);

            assertThat(result).isNotNull();
            verify(problemRepository).save(any(Problem.class));
        }

        @Test
        @DisplayName("generates slug from title: lowercased, spaces become hyphens")
        void shouldGenerateSlugFromTitle() {
            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            problemService.createProblem(buildRequest("Two Sum"), createdBy);

            verify(problemRepository).save(captor.capture());
            assertThat(captor.getValue().getSlug()).isEqualTo("two-sum");
        }

        @Test
        @DisplayName("appends suffix when base slug already exists")
        void shouldAppendSuffixOnSlugCollision() {
            // Override the default stub: base slug collides, anything else is free
            when(problemRepository.existsBySlug("two-sum")).thenReturn(true);
            when(problemRepository.existsBySlug(argThat(s -> s != null && !s.equals("two-sum"))))
                    .thenReturn(false);

            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            problemService.createProblem(buildRequest("Two Sum"), createdBy);

            verify(problemRepository).save(captor.capture());
            String savedSlug = captor.getValue().getSlug();
            assertThat(savedSlug).startsWith("two-sum-");
            assertThat(savedSlug).isNotEqualTo("two-sum");
        }

        @Test
        @DisplayName("state is always set to DRAFT on create")
        void shouldAlwaysSetStateToDraft() {
            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            problemService.createProblem(buildRequest("Two Sum"), createdBy);

            verify(problemRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(ProblemState.DRAFT);
        }

        @Test
        @DisplayName("createdBy is set to the supplied userId")
        void shouldSetCreatedByToSuppliedUserId() {
            ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
            problemService.createProblem(buildRequest("Two Sum"), createdBy);

            verify(problemRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(createdBy);
        }

        @Test
        @DisplayName("resolves topics by IDs from repository")
        void shouldResolveTopicsFromIds() {
            UUID topicId = UUID.randomUUID();
            Topic topic  = Topic.builder().id(topicId).name("Array").build();
            Set<UUID> topicIds = Set.of(topicId);

            // Build request with a non-empty topicIds so the service actually calls the repo
            CreateProblemRequest request = CreateProblemRequest.builder()
                    .title("Two Sum").description("d").difficulty(Difficulty.EASY)
                    .testCases(List.of(TestCaseRequest.builder()
                            .input("i").expected("o").isSample(true).orderIndex(0).build()))
                    .topicIds(topicIds)
                    .companyIds(new HashSet<>())
                    .build();

            when(topicRepository.findAllByIdIn(topicIds)).thenReturn(Set.of(topic));

            problemService.createProblem(request, createdBy);

            verify(topicRepository).findAllByIdIn(topicIds);
        }

        @Test
        @DisplayName("maps test cases onto the problem before saving")
        void shouldAttachTestCasesToProblem() {
            // Use a problem instance that captures the test case list properly
            TestCaseRequest tc = TestCaseRequest.builder()
                    .input("[2,7]\n9").expected("[0,1]")
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
                    .isEqualTo("[2,7]\n9");
        }
    }

    // ------------------------------------------------------------------ //
    //  updateProblem                                                       //
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

            problemService.updateProblem(problemId,
                    UpdateProblemRequest.builder().title("New Title").build());

            assertThat(problem.getTitle()).isEqualTo("New Title");
        }

        @Test
        @DisplayName("updates description when provided in request")
        void shouldUpdateDescriptionWhenProvided() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId,
                    UpdateProblemRequest.builder().description("Updated description.").build());

            assertThat(problem.getDescription()).isEqualTo("Updated description.");
        }

        @Test
        @DisplayName("updates difficulty when provided in request")
        void shouldUpdateDifficultyWhenProvided() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId,
                    UpdateProblemRequest.builder().difficulty(Difficulty.HARD).build());

            assertThat(problem.getDifficulty()).isEqualTo(Difficulty.HARD);
        }

        @Test
        @DisplayName("updates state when provided in request")
        void shouldUpdateStateWhenProvided() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId,
                    UpdateProblemRequest.builder().state(ProblemState.PUBLISHED).build());

            assertThat(problem.getState()).isEqualTo(ProblemState.PUBLISHED);
        }

        @Test
        @DisplayName("updates constraints when provided in request")
        void shouldUpdateConstraintsWhenProvided() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId,
                    UpdateProblemRequest.builder().constraints("1 <= n <= 10^4").build());

            assertThat(problem.getConstraints()).isEqualTo("1 <= n <= 10^4");
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

            assertThatThrownBy(() ->
                    problemService.updateProblem(problemId,
                            UpdateProblemRequest.builder().title("x").build()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Problem not found");
        }

        @Test
        @DisplayName("resolves topics when topicIds provided in update")
        void shouldResolveTopicsOnUpdate() {
            UUID topicId = UUID.randomUUID();
            Topic topic  = Topic.builder().id(topicId).name("DP").build();
            Set<UUID> topicIds = Set.of(topicId);

            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(topicRepository.findAllByIdIn(topicIds)).thenReturn(Set.of(topic));
            when(problemRepository.save(problem)).thenReturn(problem);
            when(problemMapper.toDetailResponse(problem)).thenReturn(detailResponse);

            problemService.updateProblem(problemId,
                    UpdateProblemRequest.builder().topicIds(topicIds).build());

            assertThat(problem.getTopics()).containsExactly(topic);
        }
    }

    // ------------------------------------------------------------------ //
    //  deleteProblem                                                       //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("deleteProblem")
    class DeleteProblem {

        @Test
        @DisplayName("calls deleteById when problem exists")
        void shouldCallDeleteByIdWhenExists() {
            when(problemRepository.existsById(problemId)).thenReturn(true);

            problemService.deleteProblem(problemId);

            verify(problemRepository).deleteById(problemId);
        }

        @Test
        @DisplayName("throws and never deletes when not found")
        void shouldThrowAndNeverDeleteWhenNotFound() {
            when(problemRepository.existsById(problemId)).thenReturn(false);

            assertThatThrownBy(() -> problemService.deleteProblem(problemId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Problem not found");

            verify(problemRepository, never()).deleteById(any());
        }
    }

    // ------------------------------------------------------------------ //
    //  getTestCasesForExecution                                            //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("getTestCasesForExecution")
    class GetTestCasesForExecution {

        @Test
        @DisplayName("returns mapped test cases for a valid problem id")
        void shouldReturnMappedTestCases() {
            TestCase tc = TestCase.builder()
                    .id(UUID.randomUUID()).input("[2,7]\n9").expected("[0,1]")
                    .isSample(true).orderIndex(0).build();
            InternalTestCaseResponse tcResponse = InternalTestCaseResponse.builder()
                    .input("[2,7]\n9").expected("[0,1]").isSample(true).orderIndex(0).build();

            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(testCaseRepository.findAllByProblemIdOrderByOrderIndexAsc(problemId))
                    .thenReturn(List.of(tc));
            when(problemMapper.toInternalTestCaseResponse(tc)).thenReturn(tcResponse);

            List<InternalTestCaseResponse> result =
                    problemService.getTestCasesForExecution(problemId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getInput()).isEqualTo("[2,7]\n9");
        }

        @Test
        @DisplayName("throws when problem does not exist")
        void shouldThrowWhenProblemNotFound() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> problemService.getTestCasesForExecution(problemId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Problem not found");
        }
    }

    // ------------------------------------------------------------------ //
    //  updateProblemState                                                  //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("updateProblemState")
    class UpdateProblemState {

        @Test
        @DisplayName("mutates state on entity and saves")
        void shouldMutateStateAndSave() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.of(problem));
            when(problemRepository.save(problem)).thenReturn(problem);

            problemService.updateProblemState(problemId, ProblemState.PUBLISHED);

            assertThat(problem.getState()).isEqualTo(ProblemState.PUBLISHED);
            verify(problemRepository).save(problem);
        }

        @Test
        @DisplayName("throws when problem not found")
        void shouldThrowWhenNotFound() {
            when(problemRepository.findById(problemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    problemService.updateProblemState(problemId, ProblemState.PUBLISHED))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Problem not found");
        }
    }
}