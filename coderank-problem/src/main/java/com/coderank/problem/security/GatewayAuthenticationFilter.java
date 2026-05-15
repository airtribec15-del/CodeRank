package com.coderank.problem.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Trusts the headers injected by the API Gateway after JWT validation.
 * The Gateway is the single point of JWT verification — this service
 * only reads the pre-validated X-User-Id and X-User-Role headers.
 *
 * Direct calls without these headers (bypassing the Gateway) will be
 * treated as unauthenticated and rejected by Spring Security.
 */
@Slf4j
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID   = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader(HEADER_USER_ID);
        String userRole = request.getHeader(HEADER_USER_ROLE);

        if (StringUtils.hasText(userId) && StringUtils.hasText(userRole)) {
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority(userRole));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Gateway auth set for userId={} role={}", userId, userRole);
        }

        filterChain.doFilter(request, response);
    }
}