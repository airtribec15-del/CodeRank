package com.coderank.submission.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the X-User-Id and X-User-Role headers injected
 * by the API Gateway (after JWT validation) and builds a Spring Security
 * Authentication so @AuthenticationPrincipal and @PreAuthorize work.
 */
@Slf4j
@Component
public class PreAuthenticatedUserFilter extends OncePerRequestFilter {

    // Must match exactly what the Gateway injects — verified from GatewayAuthenticationFilter
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_ROLE    = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String userId = request.getHeader(HEADER_USER_ID);
        String role   = request.getHeader(HEADER_ROLE);

        if (userId != null && !userId.isBlank()) {
            String authority = (role != null && !role.isBlank()) ? role : "ROLE_USER";
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority(authority))
                    );
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("Pre-authenticated user {} with role {}", userId, authority);
        }

        chain.doFilter(request, response);
    }
}