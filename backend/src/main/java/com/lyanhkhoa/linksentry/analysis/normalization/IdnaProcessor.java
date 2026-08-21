package com.lyanhkhoa.linksentry.analysis.normalization;

import com.ibm.icu.text.IDNA;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import java.util.Objects;

/**
 * Performs the analyzer's fixed, offline UTS #46 host conversion.
 *
 * <p>The option mask is intentionally explicit. IDNA validity is a normalization
 * concern; mixed-script and brand-lookalike policy remains in the analysis rules.
 * Every ICU error has the same generic invalid-host result, and ICU output is never
 * used when {@link IDNA.Info#hasErrors()} is true.
 */
public final class IdnaProcessor {

    static final int UTS46_OPTIONS = IDNA.USE_STD3_RULES
            | IDNA.CHECK_BIDI
            | IDNA.CHECK_CONTEXTJ
            | IDNA.CHECK_CONTEXTO
            | IDNA.NONTRANSITIONAL_TO_ASCII
            | IDNA.NONTRANSITIONAL_TO_UNICODE;

    private final IDNA idna = IDNA.getUTS46Instance(UTS46_OPTIONS);

    /**
     * Converts host text to the canonical nontransitional ASCII form.
     *
     * @param host host text only, never a complete URL
     * @return canonical ASCII host
     * @throws InvalidUrlException when ICU reports any IDNA error
     */
    public String toAscii(String host) {
        return convert(host, false);
    }

    /**
     * Decodes host text to the canonical nontransitional Unicode form.
     *
     * @param host host text or a single host label only, never a complete URL
     * @return canonical Unicode host text
     * @throws InvalidUrlException when ICU reports any IDNA error
     */
    public String toUnicode(String host) {
        return convert(host, true);
    }

    private String convert(String host, boolean unicode) {
        Objects.requireNonNull(host, "host");

        StringBuilder converted = new StringBuilder(host.length());
        IDNA.Info info = new IDNA.Info();
        if (unicode) {
            idna.nameToUnicode(host, converted, info);
        } else {
            idna.nameToASCII(host, converted, info);
        }
        if (info.hasErrors()) {
            throw new InvalidUrlException("URL must have a valid host");
        }
        return converted.toString();
    }
}
