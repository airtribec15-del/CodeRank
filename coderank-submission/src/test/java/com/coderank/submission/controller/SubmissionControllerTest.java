package com.coderank.submission.controller;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.submission.dto.request.RunRequest;
import com.coderank.submission.dto.request.SubmitRequest;
import com.coderank.submission.dto.response.SubmissionDetailResponse;
import com.coderank.submission.dto.response.SubmissionResponse;
import com.coderank.submission.enums.SubmissionType;
import com.coderank.submission.enums.Verdict;
import com.coderank.submission.exception.SubmissionExceptionHandler;
import com.coderank.submission.service.SubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubmissionController.class)
@Import(SubmissionExceptionHandler.class)
@DisplayName("SubmissionController")
class SubmissionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  SubmissionService submissionService;

    private ObjectMapper objectMapper;
    private UUID userId;
    private UUID submissionId;
    private UUID problemId;
    private SubmissionResponse submissionResponse;
    private SubmissionDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        userId       = UUID.randomUUID();
        submissionId = UUID.randomUUID();
        problemId    = UUID.randomUUID();

        submissionResponse = SubmissionResponse.builder()
                .submissionId(submissionId)
                .jobId(UUID.randomUUID())
                .language(Language.PYTHON)
                .submissionType(SubmissionType.RUN)
                .status(ExecutionStatus.QUEUED)
                .verdict(Verdict.PENDING)
                .createdAt(Instant.now())
                .build();

        detailResponse = SubmissionDetailResponse.builder()
                .submissionId(submissionId)
                .userId(userId)
                .jobId(UUID.randomUUID())
                .language(Language.PYTHON)
                .submissionType(SubmissionType.RUN)
                .status(ExecutionStatus.COMPLETED)
                .verdict(Verdict.ACCEPTED)
                .sourceCode("print('hello')")
                .stdout("hello\n")
                .exitCode(0)
                .executionTimeMs(45L)
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();
    }

    // ------------------------------------------------------------------ //
    //  POST /api/v1/execute                                               //
    // ------------------------------------------------------------------ //

    @Nested @DisplayName("POST /api/v1/execute")
    class Execute {

        @Test
        @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
        @DisplayName("returns 202 Accepted with SubmissionResponse on valid run request")
        void shouldReturn202OnValidRunRequest() throws Exception {
            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON)
                    .sourceCode("print('hello')")
                    .stdinInput("world")
                    .build();

            when(submissionService.run(any(RunRequest.class), any(UUID.class)))
                    .thenReturn(submissionResponse);

            mockMvc.perform(post("/api/v1/execute")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.verdict").value("PENDING"))
                    .andExpect(jsonPath("$.language").value("PYTHON"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when language is missing")
        void shouldReturn400WhenLanguageMissing() throws Exception {
            String body = "{\"sourceCode\":\"print('x')\"}";

            mockMvc.perform(post("/api/v1/execute")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when sourceCode is blank")
        void shouldReturn400WhenSourceCodeBlank() throws Exception {
            String body = "{\"language\":\"PYTHON\",\"sourceCode\":\"\"}";

            mockMvc.perform(post("/api/v1/execute")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON).sourceCode("x=1").build();

            mockMvc.perform(post("/api/v1/execute")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ //
    //  POST /api/v1/submissions                                           //
    // ------------------------------------------------------------------ //

    @Nested @DisplayName("POST /api/v1/submissions")
    class SubmitEndpoint {

        @Test
        @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
        @DisplayName("returns 202 Accepted with SubmissionResponse on valid submit")
        void shouldReturn202OnValidSubmit() throws Exception {
            SubmitRequest request = SubmitRequest.builder()
                    .problemId(problemId)
                    .language(Language.JAVA)
                    .sourceCode("class S{public static void main(String[]a){}}")
                    .build();

            SubmissionResponse submitResponse = submissionResponse.toBuilder()
                    .problemId(problemId)
                    .submissionType(SubmissionType.SUBMIT)
                    .language(Language.JAVA)
                    .build();

            when(submissionService.submit(any(SubmitRequest.class), any(UUID.class)))
                    .thenReturn(submitResponse);

            mockMvc.perform(post("/api/v1/submissions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.submissionType").value("SUBMIT"))
                    .andExpect(jsonPath("$.problemId").value(problemId.toString()));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when problemId is null")
        void shouldReturn400WhenProblemIdNull() throws Exception {
            String body = "{\"language\":\"JAVA\",\"sourceCode\":\"class S{}\"}";

            mockMvc.perform(post("/api/v1/submissions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("problemId"));
        }

        @Test
        @WithMockUser
        @DisplayName("returns 400 when sourceCode is blank in submit")
        void shouldReturn400WhenSourceCodeBlank() throws Exception {
            String body = String.format(
                    "{\"problemId\":\"%s\",\"language\":\"JAVA\",\"sourceCode\":\"\"}", problemId);

            mockMvc.perform(post("/api/v1/submissions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------ //
    //  GET /api/v1/submissions/{id}                                       //
    // ------------------------------------------------------------------ //

    @Nested @DisplayName("GET /api/v1/submissions/{id}")
    class GetById {

        @Test
        @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
        @DisplayName("returns 200 with full detail response")
        void shouldReturn200WithDetail() throws Exception {
            when(submissionService.getSubmission(any(), any(), anyBoolean()))
                    .thenReturn(detailResponse);

            mockMvc.perform(get("/api/v1/submissions/{id}", submissionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.submissionId").value(submissionId.toString()))
                    .andExpect(jsonPath("$.verdict").value("ACCEPTED"))
                    .andExpect(jsonPath("$.sourceCode").value("print('hello')"))
                    .andExpect(jsonPath("$.exitCode").value(0));
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/submissions/{id}", submissionId))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ //
    //  GET /api/v1/submissions                                            //
    // ------------------------------------------------------------------ //

    @Nested @DisplayName("GET /api/v1/submissions")
    class GetMySubmissions {

        @Test
        @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
        @DisplayName("returns 200 with paged list")
        void shouldReturnPagedList() throws Exception {
            Page<SubmissionResponse> page =
                    new PageImpl<>(List.of(submissionResponse), PageRequest.of(0, 20), 1);
            when(submissionService.getMySubmissions(any(), any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/submissions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].status").value("QUEUED"));
        }

        @Test
        @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
        @DisplayName("filters by problemId when query param provided")
        void shouldFilterByProblemId() throws Exception {
            Page<SubmissionResponse> page =
                    new PageImpl<>(List.of(submissionResponse), PageRequest.of(0, 20), 1);
            when(submissionService.getMySubmissionsForProblem(any(), any(), any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/submissions").param("problemId", problemId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));

            verify(submissionService).getMySubmissionsForProblem(any(), eq(problemId), any());
            verify(submissionService, never()).getMySubmissions(any(), any());
        }

        @Test
        @WithMockUser(username = "550e8400-e29b-41d4-a716-446655440000")
        @DisplayName("calls getMySubmissions when problemId is absent")
        void shouldCallGetMySubmissionsWithoutProblemId() throws Exception {
            Page<SubmissionResponse> page = Page.empty();
            when(submissionService.getMySubmissions(any(), any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/submissions"))
                    .andExpect(status().isOk());

            verify(submissionService).getMySubmissions(any(), any());
            verify(submissionService, never()).getMySubmissionsForProblem(any(), any(), any());
        }
    }
}
