package com.lyanhkhoa.linksentry.analysis.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicSuffixDomainResolverTest {

    private final PublicSuffixDomainResolver resolver =
            new PublicSuffixDomainResolver();

    @Test
    void extractsRegistrableDomainAfterMultiLabelCcTld() {
        DomainParts result = resolver.resolve("bbc.co.uk");

        assertThat(result.registrableDomain()).isEqualTo("bbc.co.uk");
        assertThat(result.subdomains()).isEmpty();
    }

    @Test
    void recognisesPrivateSuffix() {
        DomainParts result = resolver.resolve("example.github.io");

        assertThat(result.registrableDomain()).isEqualTo("example.github.io");
        assertThat(result.subdomains()).isEmpty();
    }

    @Test
    void keepsDeceptiveBrandLabelsAsSubdomains() {
        DomainParts result = resolver.resolve(
                "login.vietcombank.com.vn.evil-domain.xyz");

        assertThat(result.registrableDomain()).isEqualTo("evil-domain.xyz");
        assertThat(result.subdomains())
                .containsExactly("login", "vietcombank", "com", "vn");
    }

    @Test
    void returnsNoRegistrableDomainForBarePublicSuffix() {
        DomainParts result = resolver.resolve("co.uk");

        assertThat(result.registrableDomain()).isNull();
        assertThat(result.subdomains()).isEmpty();
    }

    @Test
    void returnsNoRegistrableDomainForIpLiterals() {
        assertThat(resolver.resolve("192.0.2.1").registrableDomain()).isNull();
        assertThat(resolver.resolve("[2001:db8::1]").registrableDomain()).isNull();
    }
}
