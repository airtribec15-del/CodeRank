package com.coderank.resultprocessor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

@DisplayName("ResultProcessorApplication — main entry point")
class ResultProcessorApplicationTest {

    @Test
    @DisplayName("main() delegates to SpringApplication.run with the correct class")
    void mainCallsSpringApplicationRun() {
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        String[] args = {"--server.port=0"};

        try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(eq(ResultProcessorApplication.class), any(String[].class)))
                    .thenReturn(ctx);

            ResultProcessorApplication.main(args);

            mocked.verify(() ->
                            SpringApplication.run(eq(ResultProcessorApplication.class), eq(args)),
                    times(1));
        }
    }

    @Test
    @DisplayName("ResultProcessorApplication can be instantiated (covers default ctor)")
    void canInstantiate() {
        assertThat(new ResultProcessorApplication()).isNotNull();
    }
}