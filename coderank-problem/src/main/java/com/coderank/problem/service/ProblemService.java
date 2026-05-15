package com.coderank.problem.service;

import com.coderank.common.exception.InvalidRequestException;
import com.coderank.problem.dto.request.CreateProblemRequest;
import com.coderank.problem.dto.request.UpdateProblemRequest;
import com.coderank.problem.dto.response.*;
import com.coderank.problem.entity.*;
import com.coderank.problem.enums.Difficulty;
import com.coderank.problem.enums.ProblemState;
import com.coderank.problem.mapper.ProblemMapper;
import com.coderank.problem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemService {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final ProblemRepository problemRepository;
    private final TopicRepository topicRepository;
    private final CompanyRepository companyRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProblemMapper problemMapper;

    // ─── PUBLIC: LIST (paginated, PUBLISHED only for users) ──────────────────

    @Transactional(readOnly = true)
    public Page<ProblemSummaryResponse> listPublishedProblems(Pageable pageable) {
        return problemRepository.findAllByState(ProblemState.PUBLISHED, pageable)
                .map(problemMapper::toSummaryResponse);
    }

    // ─── ADMIN: LIST (all states, filterable) ────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProblemSummaryResponse> listAllProblems(Difficulty difficulty,
                                                        ProblemState state,
                                                        Pageable pageable) {
        return problemRepository.findAllByFilters(difficulty, state, pageable)
                .map(problemMapper::toSummaryResponse);
    }

    // ─── PUBLIC: GET BY SLUG ─────────────────────────────────────────────────

    @Cacheable(value = "problems", key = "#slug")
    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblemBySlug(String slug) {
        Problem problem = problemRepository.findBySlug(slug)
                .orElseThrow(() -> new InvalidRequestException("Problem not found: " + slug));
        return problemMapper.toDetailResponse(problem);
    }

    // ─── ADMIN: GET BY ID ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblemById(UUID id) {
        Problem problem = findByIdOrThrow(id);
        return problemMapper.toDetailResponse(problem);
    }

    // ─── ADMIN: CREATE ───────────────────────────────────────────────────────

    @Transactional
    public ProblemDetailResponse createProblem(CreateProblemRequest request, UUID createdBy) {
        String slug = generateSlug(request.getTitle());
        if (problemRepository.existsBySlug(slug)) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        Set<Topic> topics = resolveTopics(request.getTopicIds());
        Set<Company> companies = resolveCompanies(request.getCompanyIds());

        Problem problem = Problem.builder()
                .title(request.getTitle())
                .slug(slug)
                .description(request.getDescription())
                .difficulty(request.getDifficulty())
                .state(ProblemState.DRAFT)
                .constraints(request.getConstraints())
                .createdBy(createdBy)
                .topics(topics)
                .companies(companies)
                .build();

        // Map examples
        if (request.getExamples() != null) {
            request.getExamples().forEach(exReq -> {
                ProblemExample example = ProblemExample.builder()
                        .problem(problem)
                        .inputText(exReq.getInputText())
                        .outputText(exReq.getOutputText())
                        .explanation(exReq.getExplanation())
                        .orderIndex(exReq.getOrderIndex())
                        .build();
                problem.getExamples().add(example);
            });
        }

        // Map test cases
        request.getTestCases().forEach(tcReq -> {
            TestCase testCase = TestCase.builder()
                    .problem(problem)
                    .input(tcReq.getInput())
                    .expected(tcReq.getExpected())
                    .isSample(tcReq.isSample())
                    .orderIndex(tcReq.getOrderIndex())
                    .build();
            problem.getTestCases().add(testCase);
        });

        Problem saved = problemRepository.save(problem);
        log.info("Problem created: {} (slug={})", saved.getId(), saved.getSlug());
        return problemMapper.toDetailResponse(saved);
    }

    // ─── ADMIN: UPDATE ───────────────────────────────────────────────────────

    @CacheEvict(value = "problems", key = "#result.slug")
    @Transactional
    public ProblemDetailResponse updateProblem(UUID id, UpdateProblemRequest request) {
        Problem problem = findByIdOrThrow(id);

        if (request.getTitle() != null)       problem.setTitle(request.getTitle());
        if (request.getDescription() != null) problem.setDescription(request.getDescription());
        if (request.getDifficulty() != null)  problem.setDifficulty(request.getDifficulty());
        if (request.getState() != null)       problem.setState(request.getState());
        if (request.getConstraints() != null) problem.setConstraints(request.getConstraints());

        if (request.getTopicIds() != null)   problem.setTopics(resolveTopics(request.getTopicIds()));
        if (request.getCompanyIds() != null) problem.setCompanies(resolveCompanies(request.getCompanyIds()));

        Problem saved = problemRepository.save(problem);
        log.info("Problem updated: {}", saved.getId());
        return problemMapper.toDetailResponse(saved);
    }

    // ─── ADMIN: DELETE ───────────────────────────────────────────────────────

    @CacheEvict(value = "problems", allEntries = true)
    @Transactional
    public void deleteProblem(UUID id) {
        if (!problemRepository.existsById(id)) {
            throw new InvalidRequestException("Problem not found: " + id);
        }
        problemRepository.deleteById(id);
        log.info("Problem deleted: {}", id);
    }

    // ─── INTERNAL: TEST CASES (for Execution Service) ────────────────────────

    @Transactional(readOnly = true)
    public List<InternalTestCaseResponse> getTestCasesForExecution(UUID problemId) {
        findByIdOrThrow(problemId); // validates existence
        return testCaseRepository.findAllByProblemIdOrderByOrderIndexAsc(problemId)
                .stream()
                .map(problemMapper::toInternalTestCaseResponse)
                .toList();
    }

    // ─── INTERNAL: STATE UPDATE (for Result Processor) ───────────────────────

    @CacheEvict(value = "problems", allEntries = true)
    @Transactional
    public void updateProblemState(UUID id, ProblemState newState) {
        Problem problem = findByIdOrThrow(id);
        problem.setState(newState);
        problemRepository.save(problem);
        log.info("Problem state updated: {} → {}", id, newState);
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private Problem findByIdOrThrow(UUID id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> new InvalidRequestException("Problem not found: " + id));
    }

    private Set<Topic> resolveTopics(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        return topicRepository.findAllByIdIn(ids);
    }

    private Set<Company> resolveCompanies(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return new HashSet<>();
        return companyRepository.findAllByIdIn(ids);
    }

    private String generateSlug(String title) {
        String normalized = Normalizer.normalize(title.toLowerCase(), Normalizer.Form.NFD);
        return NON_ALPHANUMERIC.matcher(normalized).replaceAll("-")
                .replaceAll("^-|-$", "");
    }
}