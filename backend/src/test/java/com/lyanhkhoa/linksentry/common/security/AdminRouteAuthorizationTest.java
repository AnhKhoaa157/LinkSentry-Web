package com.lyanhkhoa.linksentry.common.security;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.admin.application.AdminAuthService;
import com.lyanhkhoa.linksentry.admin.domain.AdminIdentity;
import com.lyanhkhoa.linksentry.license.api.LicenseSummaryResponse;
import com.lyanhkhoa.linksentry.license.application.LicenseAdminService;
import com.lyanhkhoa.linksentry.license.application.DeviceService;
import com.lyanhkhoa.linksentry.license.security.LicensedDeviceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"linksentry.admin.api-key=test-admin-api-key", "linksentry.ratelimit.enabled=false"})
class AdminRouteAuthorizationTest {

    private static final String ADMIN_SESSION_TOKEN = "browser-admin-session-token";
    private static final String DEVICE_CREDENTIAL = "device-credential";
    private static final UUID LICENSE_ID = UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private LicenseAdminService licenseAdminService;

    @BeforeEach
    void resetMocks() {
        given(licenseAdminService.list()).willReturn(List.of(new LicenseSummaryResponse(
                LICENSE_ID,
                "Operations",
                null,
                2,
                false,
                Instant.parse("2026-08-20T00:00:00Z"),
                0)));
    }

    @Test
    @DisplayName("a ROLE_ADMIN bearer session can manage the admin license route without the API key")
    void adminBearerCanManageLicenses() throws Exception {
        given(adminAuthService.authenticate(ADMIN_SESSION_TOKEN)).willReturn(Optional.of(adminIdentity()));

        mockMvc.perform(get("/api/v1/admin/licenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_SESSION_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].licenseId").value(LICENSE_ID.toString()))
                .andExpect(jsonPath("$[0].label").value("Operations"));

        verify(licenseAdminService).list();
    }

    @Test
    @DisplayName("a valid X-Admin-Api-Key still manages the admin license route")
    void validApiKeyCanManageLicenses() throws Exception {
        mockMvc.perform(get("/api/v1/admin/licenses").header("X-Admin-Api-Key", "test-admin-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].licenseId").value(LICENSE_ID.toString()));

        verify(licenseAdminService).list();
    }

    @Test
    @DisplayName("an anonymous admin request receives a safe JSON 401")
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/licenses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("a licensed device credential cannot access admin data")
    void licensedDeviceCredentialIsRejected() throws Exception {
        given(deviceService.authenticate(DEVICE_CREDENTIAL))
                .willReturn(Optional.of(new LicensedDeviceContext(UUID.randomUUID(), UUID.randomUUID(), Instant.MAX)));

        mockMvc.perform(get("/api/v1/admin/licenses")
                        .header(HttpHeaders.AUTHORIZATION, "Device " + DEVICE_CREDENTIAL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private static AdminIdentity adminIdentity() {
        return new AdminIdentity(UUID.randomUUID(), "ops", UUID.randomUUID(), Instant.MAX);
    }
}
