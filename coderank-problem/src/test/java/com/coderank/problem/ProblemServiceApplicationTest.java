package com.coderank.problem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ProblemServiceApplication — context loads")
class ProblemServiceApplicationTest {

    @Test
    @DisplayName("Spring application context loads without errors")
    void contextLoads() {
        // Verifies the full Spring context starts up cleanly:
        // - All beans wire correctly (Services, Repositories, Mapper, Security, Cache)
        // - JPA entity scanning and Flyway migration baseline are healthy
        // - Security filter chain initialises without exceptions
    }
}