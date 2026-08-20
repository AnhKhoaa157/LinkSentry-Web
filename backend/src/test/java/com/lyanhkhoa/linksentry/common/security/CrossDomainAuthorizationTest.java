package com.lyanhkhoa.linksentry.common.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.admin.domain.AdminIdentity;
import com.lyanhkhoa.linksentry.admin.security.AdminSessionAuthenticationFilter;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the authorization-domain fix through the real security filter chain, not a mocked slice:
 * an administrator's own session must never satisfy a licensed-device-only route, and a licensed
 * device's session must never satisfy an admin-only route. Both cases must be a genuine {@code 403}
 * from {@link ApiAccessDeniedHandler} — never a silent fallback to anonymous behaviour, an unchecked
 * cast, and never the {@code 401} used for "no credential at all" (see {@link ApiAuthenticationEntryPoint}).
 *
 * <p>Uses {@code spring-security-test}'s {@code authentication(...)} request post-processor to install
 * a fully-formed {@link Authentication} directly into the security context, bypassing the two
 * credential-lookup filters entirely (no database is needed) while still exercising {@code
 * SecurityConfig}'s real {@code authorizeHttpRequests} decision and exception translation. Runs on the
 * H2-backed {@code test} profile: every case below is rejected before any controller or database access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CrossDomainAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("an administrator's session never satisfies the licensed-device-only scan history route")
    void adminIdentityCannotRetrieveScanHistory() throws Exception {
        mockMvc.perform(get("/api/v1/scans/{scanId}", "some-scan-id").with(authentication(adminAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("an administrator's session never satisfies the licensed-device-only AI explanation route")
    void adminIdentityCannotRequestExplanation() throws Exception {
        mockMvc.perform(post("/api/v1/scans/{scanId}/explanation", "some-scan-id")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("a licensed device's session never satisfies the admin-only current-session route")
    void licensedDeviceCannotReadAdminSession() throws Exception {
        mockMvc.perform(get("/api/v1/admin-auth/session").with(authentication(licensedDeviceAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("a licensed device's session never satisfies the admin-only logout route")
    void licensedDeviceCannotLogoutAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin-auth/logout").with(authentication(licensedDeviceAuthentication())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("no credential at all still gets 401, not 403, on both cross-domain routes")
    void trulyUnauthenticatedRequestsStillGet401() throws Exception {
        mockMvc.perform(get("/api/v1/scans/{scanId}", "some-scan-id"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/admin-auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static Authentication adminAuthentication() {
        AdminIdentity identity = new AdminIdentity(UUID.randomUUID(), "ops", UUID.randomUUID(), Instant.MAX);
        return UsernamePasswordAuthenticationToken.authenticated(
                identity, null, List.of(new SimpleGrantedAuthority(AdminSessionAuthenticationFilter.ADMIN_AUTHORITY)));
    }

    private static Authentication licensedDeviceAuthentication() {
        LicensedDeviceContext device = new LicensedDeviceContext(UUID.randomUUID(), UUID.randomUUID(), Instant.MAX);
        return UsernamePasswordAuthenticationToken.authenticated(
                device, null, List.of(new SimpleGrantedAuthority(DeviceAuthenticationFilter.LICENSED_DEVICE_AUTHORITY)));
    }
}
