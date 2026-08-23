package com.lyanhkhoa.linksentry.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseLocaleTest {

    @Test
    @DisplayName("missing or blank headers default to English")
    void missingOrBlankDefaultsToEnglish() {
        assertThat(ResponseLocale.negotiate(null)).isEqualTo(ResponseLocale.ENGLISH);
        assertThat(ResponseLocale.negotiate("")).isEqualTo(ResponseLocale.ENGLISH);
        assertThat(ResponseLocale.negotiate("   ")).isEqualTo(ResponseLocale.ENGLISH);
    }

    @Test
    @DisplayName("Vietnamese language ranges select Vietnamese")
    void vietnameseRangesSelectVietnamese() {
        assertThat(ResponseLocale.negotiate("vi")).isEqualTo(ResponseLocale.VIETNAMESE);
        assertThat(ResponseLocale.negotiate("vi-VN")).isEqualTo(ResponseLocale.VIETNAMESE);
        assertThat(ResponseLocale.negotiate("vi-*"))
                .isEqualTo(ResponseLocale.VIETNAMESE);
        assertThat(ResponseLocale.negotiate("fr-FR;q=1, vi-VN;q=0.8")).isEqualTo(ResponseLocale.VIETNAMESE);
    }

    @Test
    @DisplayName("English language ranges select English")
    void englishRangesSelectEnglish() {
        assertThat(ResponseLocale.negotiate("en")).isEqualTo(ResponseLocale.ENGLISH);
        assertThat(ResponseLocale.negotiate("en-US")).isEqualTo(ResponseLocale.ENGLISH);
        assertThat(ResponseLocale.negotiate("en-*")).isEqualTo(ResponseLocale.ENGLISH);
        assertThat(ResponseLocale.negotiate("vi;q=0, en;q=0.8")).isEqualTo(ResponseLocale.ENGLISH);
    }

    @Test
    @DisplayName("unsupported, wildcard, and malformed headers fall back to English")
    void unsupportedWildcardAndMalformedFallBackToEnglish() {
        assertThat(ResponseLocale.negotiate("fr-FR")).isEqualTo(ResponseLocale.ENGLISH);
        assertThat(ResponseLocale.negotiate("*")).isEqualTo(ResponseLocale.ENGLISH);
        assertThat(ResponseLocale.negotiate("vi;q=not-a-number")).isEqualTo(ResponseLocale.ENGLISH);
        assertThat(ResponseLocale.negotiate("en_US")).isEqualTo(ResponseLocale.ENGLISH);
    }
}
