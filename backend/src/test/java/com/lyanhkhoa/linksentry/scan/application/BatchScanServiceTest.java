package com.lyanhkhoa.linksentry.scan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.common.api.ResponseLocale;
import com.lyanhkhoa.linksentry.common.config.EngineProperties;
import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialProperties;
import com.lyanhkhoa.linksentry.common.trial.TrialScanAdmission;
import com.lyanhkhoa.linksentry.scan.api.BatchScanResponse;
import com.lyanhkhoa.linksentry.scan.api.NormalizedUrlResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanDataResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

class BatchScanServiceTest {

    private static final UUID DEVICE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID LICENSE_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");

    private final ScanService scanService = mock(ScanService.class);
    private final TrialScanAdmission trialAdmission = mock(TrialScanAdmission.class);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final BatchScanService service = new BatchScanService(
            scanService,
            trialAdmission,
            new AnonymousTrialProperties(true, 3, java.time.Duration.ofDays(1)),
            validator,
            new EngineProperties("0.1.0"),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("trial items are admitted and scanned sequentially, then later items are skipped at quota")
    void trialQuotaStopsRemainingItemsInOrder() {
        when(trialAdmission.tryAdmit(eq(DEVICE_ID), any())).thenReturn(true, true, false);
        when(scanService.scan("https://first.invalid/", null, ResponseLocale.ENGLISH))
                .thenReturn(scanResponse("https://first.invalid/"));
        when(scanService.scan("https://second.invalid/", null, ResponseLocale.ENGLISH))
                .thenReturn(scanResponse("https://second.invalid/"));

        BatchScanResponse response = service.scan(
                List.of(
                        "https://first.invalid/",
                        "https://second.invalid/",
                        "https://third.invalid/",
                        "https://fourth.invalid/"),
                null,
                DEVICE_ID,
                ResponseLocale.ENGLISH);

        assertThat(response.data().results())
                .extracting(BatchScanResponse.Item::status)
                .containsExactly(
                        BatchScanResponse.Status.COMPLETE,
                        BatchScanResponse.Status.COMPLETE,
                        BatchScanResponse.Status.FAILED,
                        BatchScanResponse.Status.SKIPPED);
        assertThat(response.data().results().get(2).error().code()).isEqualTo("ANONYMOUS_TRIAL_EXHAUSTED");
        assertThat(response.meta().engineVersion()).isEqualTo("0.1.0");
        verify(scanService, times(2)).scan(any(), eq(null), eq(ResponseLocale.ENGLISH));

        InOrder order = inOrder(trialAdmission, scanService);
        order.verify(trialAdmission).tryAdmit(eq(DEVICE_ID), eq(NOW));
        order.verify(scanService).scan("https://first.invalid/", null, ResponseLocale.ENGLISH);
        order.verify(trialAdmission).tryAdmit(eq(DEVICE_ID), eq(NOW));
        order.verify(scanService).scan("https://second.invalid/", null, ResponseLocale.ENGLISH);
        order.verify(trialAdmission).tryAdmit(eq(DEVICE_ID), eq(NOW));
    }

    @Test
    @DisplayName("an invalid URL fails only its item and the following item is still scanned")
    void invalidUrlDoesNotStopFollowingItems() {
        when(trialAdmission.tryAdmit(eq(DEVICE_ID), any())).thenReturn(true, true);
        when(scanService.scan("ftp://private.invalid/secret", null, ResponseLocale.ENGLISH))
                .thenThrow(new InvalidUrlException("Unsupported scheme"));
        when(scanService.scan("https://second.invalid/", null, ResponseLocale.ENGLISH))
                .thenReturn(scanResponse("https://second.invalid/"));

        BatchScanResponse response = service.scan(
                List.of("ftp://private.invalid/secret", "https://second.invalid/"),
                null,
                DEVICE_ID,
                ResponseLocale.ENGLISH);

        assertThat(response.data().results())
                .extracting(BatchScanResponse.Item::status)
                .containsExactly(BatchScanResponse.Status.FAILED, BatchScanResponse.Status.COMPLETE);
        assertThat(response.data().results().get(0).error().code()).isEqualTo("INVALID_URL");
        assertThat(response.data().results().get(0).error().fieldErrors()).containsKey("url");
        assertThat(response.data().results().get(0).error().message()).doesNotContain("private.invalid", "secret");
    }

    @Test
    @DisplayName("blank input gets a safe per-item validation error and does not reach the analyzer")
    void validationFailureIsItemScoped() {
        when(trialAdmission.tryAdmit(eq(DEVICE_ID), any())).thenReturn(true, true);
        when(scanService.scan("https://second.invalid/", null, ResponseLocale.ENGLISH))
                .thenReturn(scanResponse("https://second.invalid/"));

        BatchScanResponse response = service.scan(
                List.of("", "https://second.invalid/"), null, DEVICE_ID, ResponseLocale.ENGLISH);

        assertThat(response.data().results())
                .extracting(BatchScanResponse.Item::status)
                .containsExactly(BatchScanResponse.Status.FAILED, BatchScanResponse.Status.COMPLETE);
        assertThat(response.data().results().get(0).error().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.data().results().get(0).error().fieldErrors()).containsKey("url");
        verify(scanService, never()).scan("", null, ResponseLocale.ENGLISH);
    }

    @Test
    @DisplayName("a quota persistence failure returns a safe item error and skips the remainder")
    void quotaFailureStopsWithoutLeakingException() {
        when(trialAdmission.tryAdmit(eq(DEVICE_ID), any()))
                .thenThrow(new DataAccessResourceFailureException("private persistence detail"));

        BatchScanResponse response = service.scan(
                List.of("https://first.invalid/", "https://second.invalid/"),
                null,
                DEVICE_ID,
                ResponseLocale.ENGLISH);

        assertThat(response.data().results())
                .extracting(BatchScanResponse.Item::status)
                .containsExactly(BatchScanResponse.Status.FAILED, BatchScanResponse.Status.SKIPPED);
        assertThat(response.data().results().get(0).error().code()).isEqualTo("TRIAL_QUOTA_UNAVAILABLE");
        assertThat(response.data().results().get(0).error().message()).doesNotContain("persistence detail");
        verify(scanService, never()).scan(any(), any(), any());
    }

    @Test
    @DisplayName("licensed scans bypass the trial quota")
    void licensedBatchDoesNotUseTrialQuota() {
        when(scanService.scan("https://first.invalid/", LICENSE_ID, ResponseLocale.ENGLISH))
                .thenReturn(scanResponse("https://first.invalid/"));

        BatchScanResponse response = service.scan(
                List.of("https://first.invalid/"), LICENSE_ID, null, ResponseLocale.ENGLISH);

        assertThat(response.data().results()).singleElement().extracting(BatchScanResponse.Item::status)
                .isEqualTo(BatchScanResponse.Status.COMPLETE);
        verify(trialAdmission, never()).tryAdmit(any(), any());
    }

    @Test
    @DisplayName("anonymous batches follow the existing scan behavior when trial quota is disabled")
    void disabledTrialQuotaDoesNotRequireDeviceOrAdmission() {
        BatchScanService unmeteredService = new BatchScanService(
                scanService,
                trialAdmission,
                new AnonymousTrialProperties(false, 3, java.time.Duration.ofDays(1)),
                validator,
                new EngineProperties("0.1.0"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(scanService.scan("https://first.invalid/", null, ResponseLocale.ENGLISH))
                .thenReturn(scanResponse("https://first.invalid/"));

        BatchScanResponse response = unmeteredService.scan(
                List.of("https://first.invalid/"), null, null, ResponseLocale.ENGLISH);

        assertThat(response.data().results()).singleElement().extracting(BatchScanResponse.Item::status)
                .isEqualTo(BatchScanResponse.Status.COMPLETE);
        verify(trialAdmission, never()).tryAdmit(any(), any());
    }

    private static ScanResponse scanResponse(String input) {
        return new ScanResponse(
                new ScanDataResponse(
                        null,
                        input,
                        new NormalizedUrlResponse("https", "example.invalid", "example.invalid", "example.invalid", null, "/", false, false),
                        0,
                        RiskLevel.LOW,
                        List.of(),
                        NOW),
                new ScanResponse.ScanMeta("0.1.0"));
    }
}
