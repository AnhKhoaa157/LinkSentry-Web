package com.lyanhkhoa.linksentry.scan.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lyanhkhoa.linksentry.common.api.ResponseLocale;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FindingCopyLocalizerTest {

    private static final String ENGLISH_TITLE = "Canonical English title";
    private static final String ENGLISH_EXPLANATION = "Canonical English explanation.";

    @Test
    @DisplayName("English keeps the canonical finding copy unchanged")
    void englishKeepsCanonicalCopy() {
        assertThat(FindingCopyLocalizer.localize(
                        "MISSING_HTTPS", ENGLISH_TITLE, ENGLISH_EXPLANATION, ResponseLocale.ENGLISH))
                .isEqualTo(new FindingCopyLocalizer.FindingCopy(ENGLISH_TITLE, ENGLISH_EXPLANATION));
    }

    @Test
    @DisplayName("all current rule IDs have Vietnamese server-owned copy")
    void allCurrentRulesHaveVietnameseCopy() {
        List<String> ruleIds = List.of(
                "BRAND_DOMAIN_MISMATCH",
                "BRAND_LOOKALIKE_HOSTNAME",
                "BRAND_IMPERSONATION_APK",
                "ENCODED_CHARACTERS",
                "EXCESSIVE_SUBDOMAINS",
                "EXCESSIVE_URL_LENGTH",
                "IP_LITERAL_HOST",
                "KNOWN_URL_SHORTENER",
                "MISSING_HTTPS",
                "PUNYCODE_HOST",
                "SPECIAL_USE_OR_PRIVATE_HOST",
                "SUSPICIOUS_KEYWORDS");

        for (String ruleId : ruleIds) {
            FindingCopyLocalizer.FindingCopy copy = FindingCopyLocalizer.localize(
                    ruleId, ENGLISH_TITLE, ENGLISH_EXPLANATION, ResponseLocale.VIETNAMESE);
            assertThat(copy.title()).as(ruleId + " title").isNotEqualTo(ENGLISH_TITLE);
            assertThat(copy.explanation()).as(ruleId + " explanation").isNotEqualTo(ENGLISH_EXPLANATION);
        }
    }

    @Test
    @DisplayName("unknown rule IDs fail closed to canonical English")
    void unknownRuleFallsBackToEnglish() {
        assertThat(FindingCopyLocalizer.localize(
                        "FUTURE_RULE", ENGLISH_TITLE, ENGLISH_EXPLANATION, ResponseLocale.VIETNAMESE))
                .isEqualTo(new FindingCopyLocalizer.FindingCopy(ENGLISH_TITLE, ENGLISH_EXPLANATION));
    }

    @Test
    @DisplayName("aggregate titles come only from the backend-owned rule catalog")
    void aggregateTitlesAreBackendOwned() {
        assertThat(FindingCopyLocalizer.localizedTitle("MISSING_HTTPS", ResponseLocale.ENGLISH))
                .contains("Connection is not encrypted");
        assertThat(FindingCopyLocalizer.localizedTitle("MISSING_HTTPS", ResponseLocale.VIETNAMESE))
                .contains("Kết nối không được mã hóa");
        assertThat(FindingCopyLocalizer.localizedTitle("FUTURE_RULE", ResponseLocale.ENGLISH)).isEmpty();
    }
}
