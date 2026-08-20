package com.lyanhkhoa.linksentry.license.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler;
import com.lyanhkhoa.linksentry.license.application.DeviceService;
import com.lyanhkhoa.linksentry.license.application.InvalidDeviceCredentialException;
import com.lyanhkhoa.linksentry.license.domain.DeviceState;
import java.time.Instant;
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
 * Covers the wiring of {@code POST /api/v1/devices} and {@code GET /api/v1/devices/me}: request handling
 * and response shape. {@link DeviceService} is mocked; its own logic is covered by
 * {@code DeviceServiceTest}.
 */
@WebMvcTest(DeviceController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceService deviceService;

    @Test
    @DisplayName("bootstrap returns the deviceId, activation code, and credential from the service")
    void bootstrapReturnsServiceResult() throws Exception {
        UUID deviceId = UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e");
        given(deviceService.bootstrap(any()))
                .willReturn(new DeviceBootstrapResponse(deviceId, "K7H9-QX3P", "raw-credential-value"));

        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientLabel\":\"web\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
                .andExpect(jsonPath("$.activationCode").value("K7H9-QX3P"))
                .andExpect(jsonPath("$.credential").value("raw-credential-value"));
    }

    @Test
    @DisplayName("bootstrap works with no request body at all")
    void bootstrapToleratesNoBody() throws Exception {
        given(deviceService.bootstrap(any()))
                .willReturn(new DeviceBootstrapResponse(UUID.randomUUID(), "AAAA-BBBB", "credential"));

        mockMvc.perform(post("/api/v1/devices")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an overlong client label is rejected before reaching the service")
    void overlongClientLabelIsRejected() throws Exception {
        String overlong = "x".repeat(33);

        mockMvc.perform(post("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientLabel\":\"" + overlong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("status forwards the Authorization: Device credential and returns the service's state")
    void statusReturnsServiceResult() throws Exception {
        given(deviceService.status(eq("abc123")))
                .willReturn(new DeviceStatusResponse(DeviceState.LICENSED, "K7H9-QX3P", Instant.parse("2027-01-01T00:00:00Z")));

        mockMvc.perform(get("/api/v1/devices/me").header("Authorization", "Device abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LICENSED"))
                .andExpect(jsonPath("$.activationCode").value("K7H9-QX3P"))
                .andExpect(jsonPath("$.licenseExpiresAt").value("2027-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("status without a recognisable Authorization header returns the safe invalid-credential error")
    void statusWithoutHeaderReturnsSafeError() throws Exception {
        mockMvc.perform(get("/api/v1/devices/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE_CREDENTIAL"));
    }

    @Test
    @DisplayName("status for a credential the service does not recognise returns the same safe invalid-credential error")
    void statusForUnknownCredentialReturnsSafeError() throws Exception {
        given(deviceService.status(eq("unknown"))).willThrow(new InvalidDeviceCredentialException());

        mockMvc.perform(get("/api/v1/devices/me").header("Authorization", "Device unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE_CREDENTIAL"))
                .andExpect(jsonPath("$.exception").doesNotExist());
    }
}
