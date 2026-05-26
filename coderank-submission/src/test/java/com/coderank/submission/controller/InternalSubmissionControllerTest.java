// src/test/java/com/coderank/submission/controller/InternalSubmissionControllerTest.java
package com.coderank.submission.controller;

import com.coderank.common.enums.ExecutionStatus;
import com.coderank.common.enums.Verdict;
import com.coderank.submission.exception.SubmissionExceptionHandler;
import com.coderank.submission.service.SubmissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalSubmissionController.class)
@Import(SubmissionExceptionHandler.class)
@DisplayName("InternalSubmissionController")
class InternalSubmissionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SubmissionService submissionService;

    @Nested
    @DisplayName("PATCH /api/v1/internal/submissions/result")
    class UpdateResult {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 204 No Content on full valid result update")
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
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 204 for each valid ExecutionStatus value")
        void shouldReturn204ForAllValidStatuses() throws Exception {
            for (ExecutionStatus status : ExecutionStatus.values()) {
                UUID jobId = UUID.randomUUID();
                mockMvc.perform(patch("/api/v1/internal/submissions/result")
                                .with(csrf())
                                .param("jobId", jobId.toString())
                                .param("status", status.name()))
                        .andExpect(status().isNoContent());
            }
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 500 when status param is an invalid enum value")
        void shouldReturn500ForInvalidStatusEnum() throws Exception {
            // Spring's MethodArgumentTypeMismatchException is not specifically handled,
            // so it falls through to the generic Exception handler → 500 INTERNAL_ERROR.
            // (Tests behaviour as-is per the strict "no business logic changes" rule.)
            mockMvc.perform(patch("/api/v1/internal/submissions/result")
                            .with(csrf())
                            .param("jobId", UUID.randomUUID().toString())
                            .param("status", "NOT_A_REAL_STATUS"))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("returns 500 when required jobId param is missing (current handler behavior)")
        void shouldReturnErrorWhenJobIdMissing() throws Exception {
            // The SubmissionExceptionHandler has no explicit handler for
            // MissingServletRequestParameterException, so it falls through to the generic
            // Exception handler → 500 INTERNAL_ERROR. This is a documented gap that
            // belongs to the handler's roadmap, not the contract under test here.
            mockMvc.perform(patch("/api/v1/internal/submissions/result")
                            .with(csrf())
                            .param("status", "COMPLETED"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
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