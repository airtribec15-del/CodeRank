package com.coderank.resultprocessor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI documentation configuration for coderank-result-processor.
 * This service has no public REST endpoints but exposes this for operational
 * visibility of its actuator and any future admin endpoints.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI resultProcessorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CodeRank Result Processor API")
                        .description(
                                "Internal pipeline service. Consumes code-execution-results " +
                                        "from Kafka, updates Submission Service, caches results in Redis, " +
                                        "and publishes state-update-events for Problem Service."
                        )
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CodeRank Engineering")
                                .email("engineering@coderank.io")));
    }
}