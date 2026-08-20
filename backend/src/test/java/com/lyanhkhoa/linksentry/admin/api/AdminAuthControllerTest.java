package com.lyanhkhoa.linksentry.admin.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.admin.application.AdminAuthService;
import com.lyanhkhoa.linksentry.admin.application.InvalidAdminCredentialsException;
import com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @Test
    @DisplayName("login validation rejects a blank username without reaching the service")
    void loginValidation() throws Exception {
        mockMvc.perform(post("/api/v1/admin-auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.username").exists());

        verifyNoInteractions(adminAuthService);
    }

    @Test
    @DisplayName("login failure does not echo the username, password, or internal details")
    void loginFailureDoesNotLeakCredentials() throws Exception {
        given(adminAuthService.login(ArgumentMatchers.any(AdminLoginRequest.class)))
                .willThrow(new InvalidAdminCredentialsException());
        String password = "correct-horse";

        mockMvc.perform(post("/api/v1/admin-auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ops\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(content().string(not(containsString(password))));
    }

    @Test
    @DisplayName("successful login response is the only response that contains the one-time bearer value")
    void authResponseShape() throws Exception {
        given(adminAuthService.login(ArgumentMatchers.any(AdminLoginRequest.class)))
                .willReturn(new AdminAuthResponse(
                        "test-only-bearer", "Bearer", Instant.parse("2026-08-20T12:30:00Z"),
                        new AdminIdentityResponse("ops")));

        mockMvc.perform(post("/api/v1/admin-auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ops\",\"password\":\"correct-horse-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-only-bearer"))
                .andExpect(jsonPath("$.admin.username").value("ops"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("a licensed device's own principal is rejected from the current-session route with a clean 403")
    void licensedDevicePrincipalCannotReadSession() throws Exception {
        LicensedDeviceContext device = new LicensedDeviceContext(UUID.randomUUID(), UUID.randomUUID(), Instant.MAX);

        mockMvc.perform(get("/api/v1/admin-auth/session")
                        .principal(new UsernamePasswordAuthenticationToken(device, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(adminAuthService);
    }

    @Test
    @DisplayName("a licensed device's own principal is rejected from the logout route with a clean 403")
    void licensedDevicePrincipalCannotLogout() throws Exception {
        LicensedDeviceContext device = new LicensedDeviceContext(UUID.randomUUID(), UUID.randomUUID(), Instant.MAX);

        mockMvc.perform(post("/api/v1/admin-auth/logout")
                        .principal(new UsernamePasswordAuthenticationToken(device, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(adminAuthService);
    }
}
