package com.lyanhkhoa.linksentry.scan.application;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.common.api.ErrorResponse;
import com.lyanhkhoa.linksentry.common.api.ResponseLocale;
import com.lyanhkhoa.linksentry.common.config.EngineProperties;
import com.lyanhkhoa.linksentry.common.trial.AnonymousTrialProperties;
import com.lyanhkhoa.linksentry.common.trial.TrialScanAdmission;
import com.lyanhkhoa.linksentry.scan.api.BatchScanResponse;
import com.lyanhkhoa.linksentry.scan.api.BatchScanResponse.Item;
import com.lyanhkhoa.linksentry.scan.api.BatchScanResponse.Status;
import com.lyanhkhoa.linksentry.scan.api.ScanDataResponse;
import com.lyanhkhoa.linksentry.scan.api.ScanRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/** Coordinates a bounded sequence of independent scans and preserves each safe outcome. */
@Service
public class BatchScanService {

    private static final Logger log = LoggerFactory.getLogger(BatchScanService.class);

    private static final String VALIDATION_MESSAGE = "The request contains invalid values.";
    private static final String INVALID_URL_MESSAGE = "The submitted value is not a supported HTTP or HTTPS URL.";
    private static final String TRIAL_EXHAUSTED_MESSAGE = "Request a license to continue scanning.";
    private static final String TRIAL_QUOTA_UNAVAILABLE_MESSAGE =
            "The trial scan quota is temporarily unavailable. Please try again shortly.";
    private static final String INTERNAL_ERROR_MESSAGE = "The request could not be completed. Please try again later.";

    private final ScanService scanService;
    private final TrialScanAdmission trialScanAdmission;
    private final AnonymousTrialProperties trialProperties;
    private final Validator validator;
    private final EngineProperties engineProperties;
    private final Clock clock;

    public BatchScanService(
            ScanService scanService,
            TrialScanAdmission trialScanAdmission,
            AnonymousTrialProperties trialProperties,
            Validator validator,
            EngineProperties engineProperties,
            Clock clock) {
        this.scanService = scanService;
        this.trialScanAdmission = trialScanAdmission;
        this.trialProperties = trialProperties;
        this.validator = validator;
        this.engineProperties = engineProperties;
        this.clock = clock;
    }

    /**
     * Processes items in order. Trial quota is admitted once for each URL, matching the single-scan route.
     * A quota failure stops the remaining items; invalid URLs remain item-scoped and do not stop the batch.
     */
    public BatchScanResponse scan(
            List<String> urls, UUID ownerLicenseId, UUID trialDeviceId, ResponseLocale locale) {
        if (ownerLicenseId == null && trialProperties.enabled() && trialDeviceId == null) {
            throw new IllegalArgumentException("A trial device identity is required for an unlicensed batch.");
        }

        List<Item> results = new ArrayList<>(urls.size());
        boolean stopped = false;
        for (int index = 0; index < urls.size(); index++) {
            if (stopped) {
                results.add(new Item(index, Status.SKIPPED, null, null));
                continue;
            }

            if (ownerLicenseId == null && trialProperties.enabled()) {
                try {
                    if (!trialScanAdmission.tryAdmit(trialDeviceId, Instant.now(clock))) {
                        results.add(new Item(
                                index,
                                Status.FAILED,
                                null,
                                ErrorResponse.of("ANONYMOUS_TRIAL_EXHAUSTED", TRIAL_EXHAUSTED_MESSAGE, newTraceId())));
                        stopped = true;
                        continue;
                    }
                } catch (DataAccessException | TransactionException exception) {
                    results.add(new Item(
                            index,
                            Status.FAILED,
                            null,
                            ErrorResponse.of(
                                    "TRIAL_QUOTA_UNAVAILABLE", TRIAL_QUOTA_UNAVAILABLE_MESSAGE, newTraceId())));
                    stopped = true;
                    continue;
                }
            }

            Set<ConstraintViolation<ScanRequest>> violations = validator.validate(new ScanRequest(urls.get(index)));
            if (!violations.isEmpty()) {
                results.add(validationFailure(index, violations));
                continue;
            }

            try {
                ScanDataResponse result = scanService.scan(urls.get(index), ownerLicenseId, locale).data();
                results.add(new Item(index, Status.COMPLETE, result, null));
            } catch (InvalidUrlException exception) {
                results.add(new Item(
                        index,
                        Status.FAILED,
                        null,
                        ErrorResponse.ofFieldErrors(
                                "INVALID_URL",
                                INVALID_URL_MESSAGE,
                                Map.of("url", "Enter a valid HTTP or HTTPS URL."),
                                newTraceId())));
            } catch (RuntimeException exception) {
                String traceId = newTraceId();
                log.error(
                        "Batch scan item failed [traceId={}, exceptionClass={}]",
                        traceId,
                        exception.getClass().getName());
                results.add(new Item(
                        index,
                        Status.FAILED,
                        null,
                        ErrorResponse.of("INTERNAL_ERROR", INTERNAL_ERROR_MESSAGE, traceId)));
                stopped = true;
            }
        }

        return new BatchScanResponse(
                new BatchScanResponse.Data(List.copyOf(results)), new BatchScanResponse.Meta(engineProperties.version()));
    }

    private static Item validationFailure(int index, Set<ConstraintViolation<ScanRequest>> violations) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        violations.stream()
                .sorted((left, right) -> left.getPropertyPath().toString().compareTo(right.getPropertyPath().toString()))
                .forEach(violation -> fieldErrors.putIfAbsent(
                        violation.getPropertyPath().toString(), violation.getMessage()));
        return new Item(
                index,
                Status.FAILED,
                null,
                ErrorResponse.ofFieldErrors("VALIDATION_ERROR", VALIDATION_MESSAGE, fieldErrors, newTraceId()));
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
