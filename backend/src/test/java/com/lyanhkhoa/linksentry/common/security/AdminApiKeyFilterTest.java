package com.lyanhkhoa.linksentry.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lyanhkhoa.linksentry.admin.security.AdminSessionAuthenticationFilter;
import com.lyanhkhoa.linksentry.common.config.AdminProperties;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Exercises {@link AdminApiKeyFilter} directly, the same style as {@code RateLimitFilterTest} and {@code
 * AnonymousTrialFilterTest}: real servlet mocks, no Spring context.
 */
class AdminApiKeyFilterTest {

    private static final String CORRECT_KEY = "correct-horse-battery-staple";

    @Test
    @DisplayName("the correct key on an admin route reaches the chain")
    void correctKeyPassesThrough() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter(new AdminProperties(CORRECT_KEY));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = adminRequest("/api/v1/admin/licenses");
        request.addHeader("X-Admin-Api-Key", CORRECT_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("a valid administrator session passes without an API key")
    void adminSessionPassesThroughWithoutApiKey() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter(new AdminProperties("configured-key"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = adminRequest("/api/v1/admin/licenses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority(AdminSessionAuthenticationFilter.ADMIN_AUTHORITY))));

        try {
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("a missing key on an admin route is rejected with a safe 401 and never reaches the chain")
    void missingKeyIsRejected() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter(new AdminProperties(CORRECT_KEY));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(adminRequest("/api/v1/admin/licenses"), response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"UNAUTHORIZED\"")
                .doesNotContain(CORRECT_KEY);
    }

    @Test
    @DisplayName("a wrong key on an admin route is rejected with the identical safe 401")
    void wrongKeyIsRejected() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter(new AdminProperties(CORRECT_KEY));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = adminRequest("/api/v1/admin/licenses");
        request.addHeader("X-Admin-Api-Key", "wrong-key-entirely");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("wrong-key-entirely", CORRECT_KEY);
    }

    @Test
    @DisplayName("a blank configured key rejects every admin request, even one presenting an empty key")
    void blankConfiguredKeyAlwaysRejects() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter(new AdminProperties(""));
        MockHttpServletRequest request = adminRequest("/api/v1/admin/licenses");
        request.addHeader("X-Admin-Api-Key", "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("a non-admin route is never gated, even without a key")
    void nonAdminRouteIsNeverGated() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter(new AdminProperties(CORRECT_KEY));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/scans"), response, chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a deeper admin sub-path is still gated")
    void deeperAdminSubPathIsGated() throws Exception {
        AdminApiKeyFilter filter = new AdminApiKeyFilter(new AdminProperties(CORRECT_KEY));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                adminRequest("/api/v1/admin/licenses/2ce16fb9-d52d-4310-8d45-a4e48f31889e/devices"),
                response,
                mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private static MockHttpServletRequest adminRequest(String path) {
        return new MockHttpServletRequest("POST", path);
    }
}
