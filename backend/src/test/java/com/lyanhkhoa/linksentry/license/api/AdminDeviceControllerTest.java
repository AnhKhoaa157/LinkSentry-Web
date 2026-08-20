package com.lyanhkhoa.linksentry.license.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler;
import com.lyanhkhoa.linksentry.license.application.DeviceAssignmentNotFoundException;
import com.lyanhkhoa.linksentry.license.application.DeviceNotFoundException;
import com.lyanhkhoa.linksentry.license.application.LicenseAdminService;
import com.lyanhkhoa.linksentry.license.domain.DeviceState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Covers the wiring of {@code /api/v1/admin/devices/**}. {@link LicenseAdminService} is mocked. */
@WebMvcTest(AdminDeviceController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminDeviceControllerTest {

    private static final UUID DEVICE_ID = UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LicenseAdminService licenseAdminService;

    @Test
    @DisplayName("lookup by activation code never requires the device's own credential")
    void lookupByActivationCodeReturnsDetail() throws Exception {
        given(licenseAdminService.findDeviceByActivationCode("K7H9-QX3P")).willReturn(new DeviceLookupResponse(
                DEVICE_ID, "K7H9-QX3P", "web", DeviceState.PENDING, null, Instant.parse("2026-08-20T00:00:00Z")));

        mockMvc.perform(get("/api/v1/admin/devices/by-code/{activationCode}", "K7H9-QX3P"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.licenseId").doesNotExist());
    }

    @Test
    @DisplayName("lookup by an unknown activation code returns a safe not-found")
    void lookupByUnknownActivationCodeReturnsSafeNotFound() throws Exception {
        given(licenseAdminService.findDeviceByActivationCode("NOPE-CODE")).willThrow(new DeviceNotFoundException());

        mockMvc.perform(get("/api/v1/admin/devices/by-code/{activationCode}", "NOPE-CODE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"));
    }

    @Test
    @DisplayName("lookup by device ID delegates to the service")
    void lookupByIdReturnsDetail() throws Exception {
        given(licenseAdminService.findDeviceById(DEVICE_ID)).willReturn(new DeviceLookupResponse(
                DEVICE_ID, "K7H9-QX3P", null, DeviceState.LICENSED, UUID.randomUUID(), Instant.parse("2026-08-20T00:00:00Z")));

        mockMvc.perform(get("/api/v1/admin/devices/{deviceId}", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LICENSED"));
    }

    @Test
    @DisplayName("a malformed device ID returns the same safe not-found as an unknown one")
    void malformedDeviceIdReturnsSafeNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/devices/{deviceId}", "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("revoke returns 204 with no body")
    void revokeReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/admin/devices/{deviceId}/revoke", DEVICE_ID)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("revoking a device with no active assignment returns a safe not-found, not a 500")
    void revokeUngrantedDeviceReturnsSafeNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new DeviceAssignmentNotFoundException())
                .when(licenseAdminService)
                .revokeDevice(eq(DEVICE_ID));

        mockMvc.perform(post("/api/v1/admin/devices/{deviceId}/revoke", DEVICE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVICE_ASSIGNMENT_NOT_FOUND"));
    }
}
