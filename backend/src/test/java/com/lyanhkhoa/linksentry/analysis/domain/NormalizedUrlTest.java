package com.lyanhkhoa.linksentry.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the invariants {@link NormalizedUrl} enforces today.
 *
 * <p>These are deliberately thin. The interesting assertions — that
 * {@code registrableDomain} really is the registered domain, that
 * {@code asciiHost} is correct for an internationalised host — belong to the
 * normalizer's own tests, and the normalizer does not exist yet (Exercises 2–3).
 */
class NormalizedUrlTest {

    @Test
    @DisplayName("subdomain depth reflects the labels left of the registrable domain")
    void reportsSubdomainDepth() {
        NormalizedUrl url = deceptiveExample();

        assertThat(url.subdomainDepth()).isEqualTo(4);
        assertThat(url.registrableDomain()).isEqualTo("evil-domain.xyz");
    }

    @Test
    @DisplayName("subdomains are defensively copied and immutable")
    void subdomainsAreImmutable() {
        List<String> mutable = new ArrayList<>(List.of("login", "example"));
        NormalizedUrl url = new NormalizedUrl(
                "https://login.example.evil.xyz/",
                "https://login.example.evil.xyz/",
                "https",
                "login.example.evil.xyz",
                "login.example.evil.xyz",
                "evil.xyz",
                mutable,
                null,
                "/",
                false,
                false,
                false);

        mutable.clear();

        assertThat(url.subdomains()).containsExactly("login", "example");
    }

    @Test
    @DisplayName("a null subdomain list normalises to empty rather than exploding later")
    void nullSubdomainsBecomeEmpty() {
        NormalizedUrl url = new NormalizedUrl(
                "https://example.com/",
                "https://example.com/",
                "https",
                "example.com",
                "example.com",
                "example.com",
                null,
                null,
                "/",
                false,
                false,
                false);

        assertThat(url.subdomains()).isEmpty();
        assertThat(url.subdomainDepth()).isZero();
    }

    @Test
    @DisplayName("the identifying strings are required")
    void requiresIdentifyingStrings() {
        assertThatNullPointerException()
                .isThrownBy(() -> new NormalizedUrl(
                        null, "https://example.com/", "https", "example.com", "example.com", "example.com",
                        List.of(), null, "/", false, false, false));

        assertThatNullPointerException()
                .isThrownBy(() -> new NormalizedUrl(
                        "https://example.com/", "https://example.com/", "https", null, "example.com", "example.com",
                        List.of(), null, "/", false, false, false));
    }

    /**
     * The URL the product exists to explain: the brand appears only in the
     * subdomain, and the registrable domain is something else entirely.
     */
    private static NormalizedUrl deceptiveExample() {
        String raw = "https://login.vietcombank.com.vn.evil-domain.xyz/account";
        return new NormalizedUrl(
                raw,
                raw,
                "https",
                "login.vietcombank.com.vn.evil-domain.xyz",
                "login.vietcombank.com.vn.evil-domain.xyz",
                "evil-domain.xyz",
                List.of("login", "vietcombank", "com", "vn"),
                null,
                "/account",
                false,
                false,
                false);
    }
}
