package com.lyanhkhoa.linksentry.license.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler;
import com.lyanhkhoa.linksentry.license.application.DeviceLimitExceededException;
import com.lyanhkhoa.linksentry.license.application.LicenseAdminService;
import com.lyanhkhoa.linksentry.license.application.LicenseNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the wiring of {@code /api/v1/admin/licenses/**}. {@link LicenseAdminService} is mocked; {@code
 * AdminApiKeyFilter}'s own gate is covered separately by {@code AdminApiKeyFilterTest}, since this slice
 * disables the security filter chain the same way every other controller slice test does.
 */
@WebMvcTest(AdminLicenseController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminLicenseControllerTest {

    private static final UUID LICENSE_ID = UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LicenseAdminService licenseAdminService;

    @Test
    @DisplayName("create delegates the request body and returns the created license")
    void createDelegatesToService() throws Exception {
        given(licenseAdminService.create(any())).willReturn(sampleLicense());

        mockMvc.perform(post("/api/v1/admin/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"jane@example.com\",\"expiresAt\":null,\"maxDevices\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseId").value(LICENSE_ID.toString()))
                .andExpect(jsonPath("$.maxDevices").value(2));
    }

    @Test
    @DisplayName("a blank label is rejected before reaching the service")
    void blankLabelIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/licenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("get returns the service's license detail")
    void getReturnsLicenseDetail() throws Exception {
        given(licenseAdminService.get(LICENSE_ID)).willReturn(sampleLicense());

        mockMvc.perform(get("/api/v1/admin/licenses/{licenseId}", LICENSE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseId").value(LICENSE_ID.toString()));
    }

    @Test
    @DisplayName("get with a malformed license ID returns the same safe not-found as an unknown one")
    void getWithMalformedIdReturnsSafeNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/licenses/{licenseId}", "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LICENSE_NOT_FOUND"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("get for an unknown license ID returns a safe not-found")
    void getForUnknownIdReturnsSafeNotFound() throws Exception {
        given(licenseAdminService.get(LICENSE_ID)).willThrow(new LicenseNotFoundException());

        mockMvc.perform(get("/api/v1/admin/licenses/{licenseId}", LICENSE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LICENSE_NOT_FOUND"));
    }

    @Test
    @DisplayName("list returns every license summary from the service")
    void listReturnsSummaries() throws Exception {
        given(licenseAdminService.list()).willReturn(List.of(
                new LicenseSummaryResponse(LICENSE_ID, "label", null, 2, false, Instant.parse("2026-08-20T00:00:00Z"), 1)));

        mockMvc.perform(get("/api/v1/admin/licenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].licenseId").value(LICENSE_ID.toString()))
                .andExpect(jsonPath("$[0].activeDeviceCount").value(1));
    }

    @Test
    @DisplayName("extend forwards the new expiry, including an explicit null for no expiry")
    void extendForwardsExpiry() throws Exception {
        given(licenseAdminService.extend(eq(LICENSE_ID), isNull())).willReturn(sampleLicense());

        mockMvc.perform(post("/api/v1/admin/licenses/{licenseId}/extend", LICENSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":null}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("revoke returns 204 with no body")
    void revokeReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/admin/licenses/{licenseId}/revoke", LICENSE_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("granting a device beyond the cap returns a safe conflict, never a stack trace or internal detail")
    void grantBeyondCapReturnsSafeConflict() throws Exception {
        given(licenseAdminService.grantDevice(eq(LICENSE_ID), any())).willThrow(new DeviceLimitExceededException());

        mockMvc.perform(post("/api/v1/admin/licenses/{licenseId}/devices", LICENSE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activationCode\":\"K7H9-QX3P\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    private static LicenseResponse sampleLicense() {
        return new LicenseResponse(LICENSE_ID, "jane@example.com", null, 2, false, Instant.parse("2026-08-20T00:00:00Z"), List.of());
    }
}
