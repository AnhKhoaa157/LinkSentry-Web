package com.lyanhkhoa.linksentry.analysis.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import org.junit.jupiter.api.Test;

class DefaultUrlNormalizerTest {

    private final UrlNormalizer normalizer = new DefaultUrlNormalizer();

    @Test
    void normalizesValidUrlAndRedactsQueryAndFragment() {
        NormalizedUrl result = normalizer.normalize("HTTP://EXAMPLE.COM:8443/CaseSensitive?token=secret#section");

        assertThat(result.originalInput())
                .isEqualTo("HTTP://EXAMPLE.COM:8443/CaseSensitive?token=secret#section");
        assertThat(result.redactedDisplayValue()).isEqualTo("http://example.com:8443/CaseSensitive");
        assertThat(result.scheme()).isEqualTo("http");
        assertThat(result.host()).isEqualTo("example.com");
        assertThat(result.asciiHost()).isEqualTo("example.com");
        assertThat(result.port()).isEqualTo(8443);
        assertThat(result.path()).isEqualTo("/CaseSensitive");
        assertThat(result.queryPresent()).isTrue();
        assertThat(result.fragmentPresent()).isTrue();
    }

    @Test
    void acceptsUrlWithoutAnExplicitPortOrPath() {
        NormalizedUrl result = normalizer.normalize("https://example.com");

        assertThat(result.redactedDisplayValue()).isEqualTo("https://example.com");
        assertThat(result.port()).isNull();
        assertThat(result.path()).isEmpty();
        assertThat(result.queryPresent()).isFalse();
        assertThat(result.fragmentPresent()).isFalse();
    }

    @Test
    void rejectsNullInput() {
        assertInvalidUrl(null);
    }

    @Test
    void rejectsBlankInput() {
        assertInvalidUrl("   ");
    }

    @Test
    void rejectsInputLongerThanMaximumLengthBeforeParsing() {
        assertInvalidUrl("x".repeat(UrlNormalizer.MAX_URL_LENGTH + 1));
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertInvalidUrl("ftp://example.com");
        assertInvalidUrl("javascript:alert(1)");
        assertInvalidUrl("data:text/plain,hello");
    }

    @Test
    void rejectsMalformedInput() {
        assertInvalidUrl("not a url");
    }

    @Test
    void rejectsUrlWithEmbeddedCredentials() {
        assertInvalidUrl("https://user:password@example.com");
    }

    @Test
    void rejectsUrlWithoutAHost() {
        assertInvalidUrl("https:///account");
    }

    @Test
    void convertsInternationalizedHostToAscii() {
        NormalizedUrl result = normalizer.normalize("https://münchen.de");

        assertThat(result.host()).isEqualTo("münchen.de");
        assertThat(result.asciiHost()).isEqualTo("xn--mnchen-3ya.de");
    }

    @Test
    void identifiesIpv4LiteralHost() {
        NormalizedUrl result = normalizer.normalize("https://127.0.0.1/login");

        assertThat(result.host()).isEqualTo("127.0.0.1");
        assertThat(result.asciiHost()).isEqualTo("127.0.0.1");
        assertThat(result.ipLiteral()).isTrue();
    }

    @Test
    void identifiesIpv6LiteralHost() {
        NormalizedUrl result = normalizer.normalize("https://[::1]/login");

        assertThat(result.host()).isEqualTo("[::1]");
        assertThat(result.asciiHost()).isEqualTo("[::1]");
        assertThat(result.ipLiteral()).isTrue();
    }

    @Test
    void identifiesDomainNameAsNotAnIpLiteral() {
        NormalizedUrl result = normalizer.normalize("https://example.com");

        assertThat(result.ipLiteral()).isFalse();
    }

    private void assertInvalidUrl(String input) {
        assertThatThrownBy(() -> normalizer.normalize(input))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageNotContaining("secret");
    }
}
