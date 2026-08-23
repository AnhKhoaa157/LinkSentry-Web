package com.lyanhkhoa.linksentry.common.api;

import java.util.Locale;

/** The only locales supported for server-owned response presentation copy. */
public enum ResponseLocale {
    ENGLISH("en"),
    VIETNAMESE("vi");

    private final String languageTag;

    ResponseLocale(String languageTag) {
        this.languageTag = languageTag;
    }

    public String languageTag() {
        return languageTag;
    }

    /**
     * Negotiates the first supported positive-weight language range in an
     * {@code Accept-Language} header.
     *
     * <p>Malformed headers, missing headers, unsupported ranges, wildcards, and
     * ranges with zero quality all fall back to English. Unsupported ranges may
     * be followed by a supported range, as permitted by normal HTTP language
     * negotiation.
     */
    public static ResponseLocale negotiate(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return ENGLISH;
        }

        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(acceptLanguage)) {
                if (range.getWeight() <= 0) {
                    continue;
                }
                String languageRange = range.getRange().toLowerCase(Locale.ROOT);
                if (matches(languageRange, "vi")) {
                    return VIETNAMESE;
                }
                if (matches(languageRange, "en")) {
                    return ENGLISH;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // A malformed client header must never make a valid scan fail.
        }
        return ENGLISH;
    }

    private static boolean matches(String languageRange, String language) {
        return languageRange.equals(language) || languageRange.startsWith(language + "-");
    }
}
