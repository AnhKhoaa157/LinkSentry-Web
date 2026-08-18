package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrandTest {

    @Test
    @DisplayName("rejects a blank id")
    void rejectsBlankId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Brand(" ", "Vietcombank", List.of("vietcombank"), List.of("vietcombank.com.vn")));
    }

    @Test
    @DisplayName("rejects a blank display name")
    void rejectsBlankDisplayName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Brand("vietcombank", " ", List.of("vietcombank"), List.of("vietcombank.com.vn")));
    }

    @Test
    @DisplayName("rejects an empty token list")
    void rejectsEmptyTokens() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Brand("vietcombank", "Vietcombank", List.of(), List.of("vietcombank.com.vn")));
    }

    @Test
    @DisplayName("rejects an empty official domain list")
    void rejectsEmptyOfficialDomains() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Brand("vietcombank", "Vietcombank", List.of("vietcombank"), List.of()));
    }

    @Test
    @DisplayName("rejects an uppercase token")
    void rejectsUppercaseToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new Brand("vietcombank", "Vietcombank", List.of("Vietcombank"), List.of("vietcombank.com.vn")));
    }

    @Test
    @DisplayName("rejects a token containing a hyphen or dot")
    void rejectsNonAlphanumericToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Brand(
                        "vietcombank", "Vietcombank", List.of("viet-combank"), List.of("vietcombank.com.vn")));
    }

    @Test
    @DisplayName("rejects a duplicate token within the same brand")
    void rejectsDuplicateToken() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Brand(
                        "vietcombank",
                        "Vietcombank",
                        List.of("vietcombank", "vietcombank"),
                        List.of("vietcombank.com.vn")));
    }

    @Test
    @DisplayName("rejects an uppercase official domain")
    void rejectsUppercaseDomain() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new Brand("vietcombank", "Vietcombank", List.of("vietcombank"), List.of("Vietcombank.com.vn")));
    }

    @Test
    @DisplayName("rejects an official domain that is not a registrable-looking domain")
    void rejectsMalformedDomain() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new Brand("vietcombank", "Vietcombank", List.of("vietcombank"), List.of("not a domain")));
    }

    @Test
    @DisplayName("rejects a duplicate official domain within the same brand")
    void rejectsDuplicateDomain() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Brand(
                "vietcombank",
                "Vietcombank",
                List.of("vietcombank"),
                List.of("vietcombank.com.vn", "vietcombank.com.vn")));
    }
}
