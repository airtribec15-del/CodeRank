package com.coderank.submission.security;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PreAuthenticatedUserFilter")
class PreAuthenticatedUserFilterTest {

    private final PreAuthenticatedUserFilter filter = new PreAuthenticatedUserFilter();

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void afterEach() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("sets Authentication when valid userId and role headers are present")
    void shouldSetAuthWhenHeadersPresent() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        String userId = "550e8400-e29b-41d4-a716-446655440000";
        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID, userId);
        request.addHeader(PreAuthenticatedUserFilter.HEADER_ROLE, "ROLE_USER");

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("sets ROLE_USER as default when role header is absent")
    void shouldDefaultToRoleUserWhenRoleMissing() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID,
                "550e8400-e29b-41d4-a716-446655440000");

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    @DisplayName("sets ROLE_ADMIN when admin role header is present")
    void shouldSetAdminRole() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID,
                "550e8400-e29b-41d4-a716-446655440001");
        request.addHeader(PreAuthenticatedUserFilter.HEADER_ROLE, "ROLE_ADMIN");

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("does NOT set Authentication when userId header is absent")
    void shouldNotSetAuthWhenUserIdMissing() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("does NOT set Authentication when userId header is blank")
    void shouldNotSetAuthWhenUserIdBlank() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        request.addHeader(PreAuthenticatedUserFilter.HEADER_USER_ID, "   ");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("always continues filter chain even without headers")
    void shouldAlwaysContinueChain() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }
}
