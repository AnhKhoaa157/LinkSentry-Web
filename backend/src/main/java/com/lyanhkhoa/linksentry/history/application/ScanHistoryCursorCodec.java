package com.lyanhkhoa.linksentry.history.application;

import com.lyanhkhoa.linksentry.history.domain.ScanHistoryCursor;
import java.nio.ByteBuffer;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Versioned, URL-safe encoding for a history keyset cursor. */
final class ScanHistoryCursorCodec {

    private static final byte VERSION = 1;
    private static final int PAYLOAD_SIZE = 29;
    private static final int MAX_ENCODED_LENGTH = 64;

    private ScanHistoryCursorCodec() {}

    static String encode(ScanHistoryCursor cursor) {
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_SIZE)
                .put(VERSION)
                .putLong(cursor.analyzedAt().getEpochSecond())
                .putInt(cursor.analyzedAt().getNano())
                .putLong(cursor.scanId().getMostSignificantBits())
                .putLong(cursor.scanId().getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array());
    }

    static ScanHistoryCursor decode(String rawCursor) {
        if (rawCursor == null || rawCursor.isBlank() || rawCursor.length() > MAX_ENCODED_LENGTH) {
            throw InvalidScanHistoryRequestException.invalidCursor();
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(rawCursor);
            if (decoded.length != PAYLOAD_SIZE) {
                throw InvalidScanHistoryRequestException.invalidCursor();
            }

            ByteBuffer payload = ByteBuffer.wrap(decoded);
            if (payload.get() != VERSION) {
                throw InvalidScanHistoryRequestException.invalidCursor();
            }

            long epochSecond = payload.getLong();
            int nano = payload.getInt();
            UUID scanId = new UUID(payload.getLong(), payload.getLong());
            ScanHistoryCursor cursor = new ScanHistoryCursor(Instant.ofEpochSecond(epochSecond, nano), scanId);
            if (!encode(cursor).equals(rawCursor)) {
                throw InvalidScanHistoryRequestException.invalidCursor();
            }
            return cursor;
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw InvalidScanHistoryRequestException.invalidCursor();
        }
    }
}
