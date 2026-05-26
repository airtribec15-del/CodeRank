// src/test/java/com/coderank/submission/controller/SubmissionControllerTest.java
package com.coderank.submission.controller;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Language;
import com.coderank.common.enums.Verdict;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.submission.dto.request.RunRequest;
import com.coderank.submission.dto.request.SubmitRequest;
import com.coderank.submission.dto.response.JobResultResponse;
import com.coderank.submission.dto.response.SubmissionDetailResponse;
import com.coderank.submission.dto.response.SubmissionResponse;
import com.coderank.submission.enums.SubmissionType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
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
    private UUID jobId;
    private SubmissionResponse submissionResponse;
    private SubmissionDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        userId       = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        submissionId = UUID.randomUUID();
        problemId    = UUID.randomUUID();
        jobId        = UUID.randomUUID();

        submissionResponse = SubmissionResponse.builder()
                .submissionId(submissionId)
                .jobId(jobId)
                .language(Language.PYTHON)
                .submissionType(SubmissionType.RUN)
                .status(ExecutionStatus.QUEUED)
                .verdict(Verdict.PENDING)
                .createdAt(Instant.now())
                .build();

        detailResponse = SubmissionDetailResponse.builder()
                .submissionId(submissionId)
                .userId(userId)
                .jobId(jobId)
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

    /**
     * Returns an Authentication whose principal is the userId String — matching the
     * runtime contract produced by PreAuthenticatedUserFilter. This makes
     * {@code @AuthenticationPrincipal String userId} resolve correctly under MockMvc.
     */
    private static Authentication userAuth(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder rb, String userId) {
        return rb.with(authentication(userAuth(userId, "USER"))).with(csrf());
    }

    private static MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder rb, String userId) {
        return rb.with(authentication(userAuth(userId, "ADMIN"))).with(csrf());
    }

    // ------------------------------------------------------------------ //
    //  POST /api/v1/execute                                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("POST /api/v1/execute")
    class Execute {

        @Test
        @DisplayName("returns 202 Accepted with SubmissionResponse on valid run request")
        void shouldReturn202OnValidRunRequest() throws Exception {
            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON)
                    .sourceCode("print('hello')")
                    .stdinInput("world")
                    .build();

            when(submissionService.run(any(RunRequest.class), any(UUID.class)))
                    .thenReturn(submissionResponse);

            mockMvc.perform(asUser(post("/api/v1/execute"), userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.verdict").value("PENDING"))
                    .andExpect(jsonPath("$.language").value("python"));

            verify(submissionService).run(any(RunRequest.class), eq(userId));
        }

        @Test
        @DisplayName("returns 400 when language is missing")
        void shouldReturn400WhenLanguageMissing() throws Exception {
            String body = "{\"sourceCode\":\"print('x')\"}";

            mockMvc.perform(asUser(post("/api/v1/execute"), userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when sourceCode is blank")
        void shouldReturn400WhenSourceCodeBlank() throws Exception {
            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON)
                    .sourceCode("")
                    .build();

            mockMvc.perform(asUser(post("/api/v1/execute"), userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            RunRequest request = RunRequest.builder()
                    .language(Language.PYTHON)
                    .sourceCode("print('x')")
                    .build();

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

    @Nested
    @DisplayName("POST /api/v1/submissions")
    class Submit {

        @Test
        @DisplayName("returns 202 Accepted on valid submit request")
        void shouldReturn202OnValidSubmit() throws Exception {
            SubmitRequest request = SubmitRequest.builder()
                    .problemId(problemId)
                    .language(Language.JAVA)
                    .sourceCode("class S {}")
                    .build();

            when(submissionService.submit(any(SubmitRequest.class), any(UUID.class)))
                    .thenReturn(submissionResponse);

            mockMvc.perform(asUser(post("/api/v1/submissions"), userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("QUEUED"));

            verify(submissionService).submit(any(SubmitRequest.class), eq(userId));
        }

        @Test
        @DisplayName("returns 400 when problemId is null")
        void shouldReturn400WhenProblemIdNull() throws Exception {
            SubmitRequest request = SubmitRequest.builder()
                    .language(Language.JAVA)
                    .sourceCode("class S {}")
                    .build();

            mockMvc.perform(asUser(post("/api/v1/submissions"), userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when sourceCode is blank")
        void shouldReturn400WhenSourceCodeBlank() throws Exception {
            SubmitRequest request = SubmitRequest.builder()
                    .problemId(problemId)
                    .language(Language.JAVA)
                    .sourceCode("")
                    .build();

            mockMvc.perform(asUser(post("/api/v1/submissions"), userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            SubmitRequest request = SubmitRequest.builder()
                    .problemId(problemId)
                    .language(Language.JAVA)
                    .sourceCode("class S {}")
                    .build();

            mockMvc.perform(post("/api/v1/submissions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ //
    //  GET /api/v1/submissions/{id}                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("GET /api/v1/submissions/{id}")
    class GetSubmission {

        @Test
        @DisplayName("returns 200 with detail response for owner")
        void shouldReturn200WithDetail() throws Exception {
            when(submissionService.getSubmission(eq(submissionId), eq(userId), eq(false)))
                    .thenReturn(detailResponse);

            mockMvc.perform(asUser(get("/api/v1/submissions/{id}", submissionId), userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.submissionId").value(submissionId.toString()))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.verdict").value("ACCEPTED"));
        }

        @Test
        @DisplayName("returns 400 on InvalidRequestException (access denied)")
        void shouldReturn400OnAccessDenied() throws Exception {
            when(submissionService.getSubmission(any(), any(), anyBoolean()))
                    .thenThrow(new InvalidRequestException("Access denied to submission " + submissionId));

            mockMvc.perform(asUser(get("/api/v1/submissions/{id}", submissionId), userId.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/submissions/{id}", submissionId))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ //
    //  GET /api/v1/submissions/{id}/result                                //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("GET /api/v1/submissions/{id}/result")
    class GetJobResult {

        @Test
        @DisplayName("returns 200 with cached job result")
        void shouldReturn200WithJobResult() throws Exception {
            JobResultResponse result = JobResultResponse.builder()
                    .jobId(jobId)
                    .submissionId(submissionId)
                    .status(ExecutionStatus.COMPLETED)
                    .verdict(Verdict.ACCEPTED)
                    .source("cache")
                    .build();

            when(submissionService.getJobResult(eq(submissionId), eq(userId), eq(false)))
                    .thenReturn(result);

            mockMvc.perform(asUser(get("/api/v1/submissions/{id}/result", submissionId), userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.verdict").value("ACCEPTED"))
                    .andExpect(jsonPath("$.source").value("cache"));
        }

        @Test
        @DisplayName("returns 200 with QUEUED status from cache")
        void shouldReturn200WithQueuedStatus() throws Exception {
            JobResultResponse result = JobResultResponse.builder()
                    .jobId(jobId)
                    .submissionId(submissionId)
                    .status(ExecutionStatus.QUEUED)
                    .verdict(Verdict.PENDING)
                    .source("cache")
                    .build();

            when(submissionService.getJobResult(any(), any(), anyBoolean()))
                    .thenReturn(result);

            mockMvc.perform(asUser(get("/api/v1/submissions/{id}/result", submissionId), userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.verdict").value("PENDING"));
        }

        @Test
        @DisplayName("returns 400 on InvalidRequestException")
        void shouldReturn400OnInvalidRequest() throws Exception {
            when(submissionService.getJobResult(any(), any(), anyBoolean()))
                    .thenThrow(new InvalidRequestException("Access denied"));

            mockMvc.perform(asUser(get("/api/v1/submissions/{id}/result", submissionId), userId.toString()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/submissions/{id}/result", submissionId))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ //
    //  GET /api/v1/submissions                                            //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("GET /api/v1/submissions")
    class ListSubmissions {

        @Test
        @DisplayName("returns 200 with paged submissions when problemId absent")
        void shouldReturnAllSubmissions() throws Exception {
            Page<SubmissionResponse> page = new PageImpl<>(List.of(submissionResponse));
            when(submissionService.getMySubmissions(any(), any())).thenReturn(page);

            mockMvc.perform(asUser(get("/api/v1/submissions"), userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].submissionId").value(submissionId.toString()));

            verify(submissionService).getMySubmissions(eq(userId), any());
            verify(submissionService, never()).getMySubmissionsForProblem(any(), any(), any());
        }

        @Test
        @DisplayName("returns 200 with paged submissions filtered by problemId")
        void shouldReturnSubmissionsFilteredByProblemId() throws Exception {
            Page<SubmissionResponse> page = new PageImpl<>(List.of(submissionResponse));
            when(submissionService.getMySubmissionsForProblem(any(), eq(problemId), any()))
                    .thenReturn(page);

            mockMvc.perform(asUser(get("/api/v1/submissions"), userId.toString())
                            .param("problemId", problemId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].submissionId").value(submissionId.toString()));

            verify(submissionService).getMySubmissionsForProblem(eq(userId), eq(problemId), any());
            verify(submissionService, never()).getMySubmissions(any(), any());
        }

        @Test
        @DisplayName("returns 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/submissions"))
                    .andExpect(status().isUnauthorized());
        }
    }
}