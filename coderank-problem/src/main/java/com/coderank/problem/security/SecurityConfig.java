package com.coderank.problem.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(gatewayAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Swagger / Actuator — open
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health"
                        ).permitAll()

                        // Internal endpoints — accessible only within the cluster
                        // (Gateway blocks /api/v1/internal/** externally)
                        .requestMatchers("/api/v1/internal/**").hasAnyAuthority(
                                "ROLE_USER", "ROLE_ADMIN"
                        )

                        // Topics & Companies — read open, write ADMIN only
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/topics/**",
                                "/api/v1/companies/**"
                        ).hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")

                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/topics/**",
                                "/api/v1/companies/**"
                        ).hasAuthority("ROLE_ADMIN")

                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/topics/**",
                                "/api/v1/companies/**"
                        ).hasAuthority("ROLE_ADMIN")

                        // Problems — GET open to authenticated, mutations ADMIN only
                        .requestMatchers(HttpMethod.GET, "/api/v1/problems/**")
                        .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/v1/problems/**")
                        .hasAuthority("ROLE_ADMIN")

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/problems/**")
                        .hasAuthority("ROLE_ADMIN")

                        .requestMatchers(HttpMethod.DELETE, "/api/v1/problems/**")
                        .hasAuthority("ROLE_ADMIN")

                        .anyRequest().authenticated()
                );

        return http.build();
    }
}