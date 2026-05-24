package com.coderank.submission.controller;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.submission.enums.Verdict;
import com.coderank.submission.exception.SubmissionExceptionHandler;
import com.coderank.submission.service.SubmissionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalSubmissionController.class)
@Import(SubmissionExceptionHandler.class)
@DisplayName("InternalSubmissionController")
class InternalSubmissionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  SubmissionService submissionService;

    @Nested @DisplayName("PATCH /api/v1/internal/submissions/result")
    class UpdateResult {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 204 No Content on valid result update")
        void shouldReturn204OnValidUpdate() throws Exception {
            UUID jobId = UUID.randomUUID();
            doNothing().when(submissionService).updateSubmissionResult(
                    any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(patch("/api/v1/internal/submissions/result")
                            .with(csrf())
                            .param("jobId", jobId.toString())
                            .param("status", "COMPLETED")
                            .param("stdout", "[0,1]")
                            .param("stderr", "")
                            .param("exitCode", "0")
                            .param("executionTimeMs", "130")
                            .param("verdict", "ACCEPTED"))
                    .andExpect(status().isNoContent());

            verify(submissionService).updateSubmissionResult(
                    eq(jobId),
                    eq(ExecutionStatus.COMPLETED),
                    eq("[0,1]"),
                    eq(""),
                    eq(0),
                    eq(130L),
                    eq(Verdict.ACCEPTED));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 204 with only required params (no optional fields)")
        void shouldReturn204WithMinimalParams() throws Exception {
            UUID jobId = UUID.randomUUID();
            doNothing().when(submissionService).updateSubmissionResult(
                    any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(patch("/api/v1/internal/submissions/result")
                            .with(csrf())
                            .param("jobId", jobId.toString())
                            .param("status", "FAILED"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("uses PENDING as default verdict when verdict param is absent")
        void shouldUsePendingAsDefaultVerdict() throws Exception {
            UUID jobId = UUID.randomUUID();
            doNothing().when(submissionService).updateSubmissionResult(
                    any(), any(), any(), any(), any(), any(), any());

            mockMvc.perform(patch("/api/v1/internal/submissions/result")
                            .with(csrf())
                            .param("jobId", jobId.toString())
                            .param("status", "COMPLETED"))
                    .andExpect(status().isNoContent());

            verify(submissionService).updateSubmissionResult(
                    eq(jobId), any(), any(), any(), any(), any(), eq(Verdict.PENDING));
        }

        @Test
        @DisplayName("returns 401 when called without authentication")
        void shouldReturn401WithoutAuth() throws Exception {
            mockMvc.perform(patch("/api/v1/internal/submissions/result")
                            .with(csrf())
                            .param("jobId", UUID.randomUUID().toString())
                            .param("status", "COMPLETED"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
