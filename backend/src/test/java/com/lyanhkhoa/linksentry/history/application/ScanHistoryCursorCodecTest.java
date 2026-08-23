package com.lyanhkhoa.linksentry.history.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lyanhkhoa.linksentry.history.domain.ScanHistoryCursor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScanHistoryCursorCodecTest {

    private static final ScanHistoryCursor CURSOR = new ScanHistoryCursor(
            Instant.parse("2026-08-24T01:02:03.123456789Z"),
            UUID.fromString("2ce16fb9-d52d-4310-8d45-a4e48f31889e"));

    @Test
    @DisplayName("cursor encoding round-trips the full timestamp and UUID deterministically")
    void cursorRoundTripsDeterministically() {
        String first = ScanHistoryCursorCodec.encode(CURSOR);
        String second = ScanHistoryCursorCodec.encode(CURSOR);

        assertThat(first).isEqualTo(second).doesNotContain("=", "+", "/");
        assertThat(ScanHistoryCursorCodec.decode(first)).isEqualTo(CURSOR);
    }

    @Test
    @DisplayName("blank, malformed, padded, and oversized cursor inputs share one safe failure")
    void invalidCursorFormsAreRejected() {
        String valid = ScanHistoryCursorCodec.encode(CURSOR);

        for (String invalid : new String[] {"", "not-a-cursor", valid + "=", "a".repeat(65)}) {
            assertThatThrownBy(() -> ScanHistoryCursorCodec.decode(invalid))
                    .isInstanceOf(InvalidScanHistoryRequestException.class)
                    .hasMessage("Invalid scan history request");
        }
    }
}
