package com.lyanhkhoa.linksentry.history.application;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Parses an opaque scan ID path value the same way everywhere it is accepted.
 *
 * <p>Shared by {@code scan.application.ScanService} and
 * {@code explanation.application.ExplanationService} so a malformed, non-UUID
 * path value produces the identical safe {@link ScanNotFoundException} from
 * every caller, rather than two implementations that could quietly drift.
 */
public final class ScanIdParser {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private ScanIdParser() {}

    /**
     * @param rawScanId the path value as submitted
     * @return the parsed UUID
     * @throws ScanNotFoundException when {@code rawScanId} is not a canonical UUID
     */
    public static UUID parse(String rawScanId) {
        if (rawScanId == null || !CANONICAL_UUID.matcher(rawScanId).matches()) {
            throw new ScanNotFoundException();
        }
        try {
            return UUID.fromString(rawScanId);
        } catch (IllegalArgumentException exception) {
            // Do not retain or expose a malformed path value in an exception cause.
            throw new ScanNotFoundException();
        }
    }
}
