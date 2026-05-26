// src/test/java/com/coderank/submission/security/PreAuthenticatedUserFilterTest.java
package com.coderank.submission.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreAuthenticatedUserFilter")
class PreAuthenticatedUserFilterTest {

    private final PreAuthenticatedUserFilter filter = new PreAuthenticatedUserFilter();

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("sets Authentication with userId as principal and ROLE_USER when headers present")
    void shouldAuthenticateWithUserRole() throws Exception {
        String userId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID, userId);
        request.addHeader(PreAuthenticatedUserFilter.HEADER_ROLE, "ROLE_USER");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("sets ROLE_ADMIN authority when X-User-Role is ROLE_ADMIN")
    void shouldAuthenticateWithAdminRole() throws Exception {
        String userId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID, userId);
        request.addHeader(PreAuthenticatedUserFilter.HEADER_ROLE, "ROLE_ADMIN");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("defaults to ROLE_USER when X-User-Role header is absent")
    void shouldDefaultToRoleUserWhenRoleHeaderAbsent() throws Exception {
        String userId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID, userId);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("defaults to ROLE_USER when X-User-Role header is blank")
    void shouldDefaultToRoleUserWhenRoleHeaderBlank() throws Exception {
        String userId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID, userId);
        request.addHeader(PreAuthenticatedUserFilter.HEADER_ROLE, "   ");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("does NOT set Authentication when X-Authenticated-User-Id header is absent")
    void shouldNotAuthenticateWhenUserIdHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("does NOT set Authentication when X-Authenticated-User-Id is blank whitespace")
    void shouldNotAuthenticateWhenUserIdIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID, "   ");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("does NOT set Authentication when X-Authenticated-User-Id is empty string")
    void shouldNotAuthenticateWhenUserIdIsEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID, "");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("always passes the request down the filter chain (no headers)")
    void shouldAlwaysCallFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(chain.getResponse()).isSameAs(response);
    }

    @Test
    @DisplayName("always passes the request down the filter chain (with auth headers)")
    void shouldAlwaysCallFilterChainWithAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID, UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}