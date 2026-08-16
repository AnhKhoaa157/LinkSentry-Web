package com.lyanhkhoa.linksentry.scan.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleExecutionException;
import com.lyanhkhoa.linksentry.analysis.domain.Severity;
import com.lyanhkhoa.linksentry.common.exception.GlobalExceptionHandler;
import com.lyanhkhoa.linksentry.history.application.ScanNotFoundException;
import com.lyanhkhoa.linksentry.scan.application.ScanService;
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
 * Covers the wiring of {@code POST /api/v1/scans}: request validation, delegation
 * to {@link ScanService}, exception mapping, and response shape.
 *
 * <p>{@link ScanService} is mocked — its own analysis correctness is covered by
 * the {@code analysis.*} unit tests. This slice only proves the controller does
 * not leak raw input and maps every documented failure mode correctly.
 */
@WebMvcTest(ScanController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanService scanService;

    @Test
    @DisplayName("a valid URL returns 200 with the analysis result")
    void validUrlReturnsResult() throws Exception {
        given(scanService.scan(anyString())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(20))
                .andExpect(jsonPath("$.data.riskLevel").value("MODERATE"))
                .andExpect(jsonPath("$.data.findings[0].ruleId").value("MISSING_HTTPS"))
                .andExpect(jsonPath("$.meta.engineVersion").value("0.1.0"));
    }

    @Test
    @DisplayName("a blank url is rejected before reaching the service")
    void blankUrlIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.url").exists());
    }

    @Test
    @DisplayName("a url over the maximum length is rejected before reaching the service")
    void overlongUrlIsRejected() throws Exception {
        String overlong = "https://example.com/" + "a".repeat(2048);

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + overlong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("an unsupported scheme is reported as INVALID_URL")
    void unsupportedSchemeReturnsInvalidUrl() throws Exception {
        given(scanService.scan(anyString())).willThrow(new InvalidUrlException("Only http and https are supported"));

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com/file\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_URL"))
                .andExpect(jsonPath("$.fieldErrors.url").exists());
    }

    @Test
    @DisplayName("a rule failure returns a generic 500 with no partial result, ever")
    void ruleFailureReturnsGenericErrorWithNoPartialResult() throws Exception {
        given(scanService.scan(anyString())).willThrow(new RuleExecutionException("EXCESSIVE_URL_LENGTH", "IllegalStateException"));

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("The request could not be completed. Please try again later."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.findings").doesNotExist());
    }

    @Test
    @DisplayName("malformed JSON is rejected without reaching the service")
    void malformedJsonIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("the response never exposes the raw submitted input, only the redacted value")
    void responseNeverExposesRawInput() throws Exception {
        given(scanService.scan(anyString())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account?token=secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.input").value("https://example.com/account"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("token=secret"))))
                .andExpect(jsonPath("$.data.originalInput").doesNotExist());
    }

    @Test
    @DisplayName("a retained scan is returned with the existing response shape")
    void retainedScanReturnsExistingResponseShape() throws Exception {
        UUID scanId = UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e");
        given(scanService.get(scanId.toString())).willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/scans/{scanId}", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanId").value(scanId.toString()))
                .andExpect(jsonPath("$.data.input").value("https://example.com/account"))
                .andExpect(jsonPath("$.data.findings[0].ruleId").value("MISSING_HTTPS"))
                .andExpect(jsonPath("$.meta.engineVersion").value("0.1.0"));
    }

    @Test
    @DisplayName("a missing, malformed, or expired scan ID returns safe SCAN_NOT_FOUND")
    void missingScanReturnsSafeNotFound() throws Exception {
        given(scanService.get("not-a-uuid")).willThrow(new ScanNotFoundException());

        mockMvc.perform(get("/api/v1/scans/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested scan could not be found."))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    private static ScanResponse sampleResponse() {
        NormalizedUrlResponse normalized = new NormalizedUrlResponse(
                "https", "example.com", "example.com", "example.com", null, "/account", false, false);
        FindingResponse finding = new FindingResponse(
                "MISSING_HTTPS", Severity.LOW, 20, "Connection is not encrypted", "explanation", null);
        ScanDataResponse data = new ScanDataResponse(
                UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e"),
                "https://example.com/account",
                normalized,
                20,
                RiskLevel.MODERATE,
                List.of(finding),
                Instant.parse("2026-08-12T00:00:00Z"));
        return new ScanResponse(data, new ScanResponse.ScanMeta("0.1.0"));
    }
}
