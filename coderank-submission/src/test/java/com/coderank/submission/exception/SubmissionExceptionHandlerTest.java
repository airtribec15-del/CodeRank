// src/test/java/com/coderank/submission/exception/SubmissionExceptionHandlerTest.java
package com.coderank.submission.exception;

import com.coderank.common.exception.CodeRankException;
import com.coderank.common.exception.InvalidRequestException;
import com.coderank.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("SubmissionExceptionHandler")
class SubmissionExceptionHandlerTest {

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/invalid")
        public void throwInvalid() {
            throw new InvalidRequestException("Bad input supplied");
        }

        @GetMapping("/notfound")
        public void throwNotFound() {
            throw new ResourceNotFoundException("Submission", "abc-123");
        }

        @GetMapping("/coderank")
        public void throwCodeRank() {
            throw new CodeRankException("Custom error",
                    HttpStatus.CONFLICT, "CONFLICT_CODE");
        }

        @GetMapping("/generic")
        public void throwGeneric() {
            throw new RuntimeException("unexpected internal error");
        }

        @PostMapping("/validation")
        public void triggerValidation(
                @org.springframework.web.bind.annotation.RequestBody
                @jakarta.validation.Valid
                ValidBody body) {}

        record ValidBody(
                @jakarta.validation.constraints.NotBlank(message = "name required") String name) {}
    }

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new SubmissionExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("handles InvalidRequestException → 400 with INVALID_REQUEST error code")
    void shouldHandle400ForInvalidRequest() throws Exception {
        mockMvc.perform(get("/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Bad input supplied"))
                .andExpect(jsonPath("$.path").value("/test/invalid"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("handles ResourceNotFoundException → 404 with RESOURCE_NOT_FOUND error code")
    void shouldHandle404ForResourceNotFound() throws Exception {
        mockMvc.perform(get("/test/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Submission not found with identifier: abc-123"));
    }

    @Test
    @DisplayName("handles CodeRankException with custom status and error code")
    void shouldHandleGenericCodeRankException() throws Exception {
        mockMvc.perform(get("/test/coderank"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT_CODE"))
                .andExpect(jsonPath("$.message").value("Custom error"));
    }

    @Test
    @DisplayName("handles generic Exception → 500 with INTERNAL_ERROR code")
    void shouldHandle500ForGenericException() throws Exception {
        mockMvc.perform(get("/test/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("handles MethodArgumentNotValidException → 400 with field errors list")
    void shouldHandle400ForBeanValidation() throws Exception {
        String body = objectMapper.writeValueAsString(new TestController.ValidBody(""));

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("name required"));
    }
}