package com.lyanhkhoa.linksentry.analysis.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

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
    void usesNontransitionalUts46MappingForSharpS() {
        NormalizedUrl result = normalizer.normalize("https://fa\u00DF.de");

        assertThat(result.asciiHost()).isEqualTo("xn--fa-hia.de");
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

    @Test
    void canonicalizesAChostTrailingDot() {
        NormalizedUrl result = normalizer.normalize("https://EXAMPLE.COM./path");

        assertThat(result.host()).isEqualTo("example.com");
        assertThat(result.asciiHost()).isEqualTo("example.com");
        assertThat(result.registrableDomain()).isEqualTo("example.com");
        assertThat(result.redactedDisplayValue()).isEqualTo("https://example.com/path");
    }

    @Test
    void acceptsTheFullPortRangeAndRejectsPortsOutsideIt() {
        assertThat(normalizer.normalize("https://example.com:0").port()).isZero();
        assertThat(normalizer.normalize("https://example.com:65535").port()).isEqualTo(65535);

        assertInvalidUrl("https://example.com:65536");
        assertInvalidUrl("https://example.com:");
        assertInvalidUrl("https://example.com:abc");
    }

    @Test
    void rejectsMalformedHostLabelsAndBracketedNonIpv6Hosts() {
        assertInvalidUrl("https://a..example.com");
        assertInvalidUrl("https://-example.com");
        assertInvalidUrl("https://example-.com");
        assertInvalidUrl("https://256.256.256.256");
        assertInvalidUrl("https://[not-an-ip]");
    }

    @Test
    void rejectsIdnaErrorsWithTheGenericHostContract() {
        assertThatThrownBy(() -> normalizer.normalize(
                        "https://xn--0.example/path?token=IDNA_SECRET#fragment"))
                .isExactlyInstanceOf(InvalidUrlException.class)
                .hasMessage("URL must have a valid host")
                .hasMessageNotContaining("xn--0")
                .hasMessageNotContaining("IDNA_SECRET")
                .hasMessageNotContaining("INVALID_ACE_LABEL")
                .hasNoCause();
    }

    @Test
    void acceptsAnIpv4MappedIpv6Literal() {
        NormalizedUrl result = normalizer.normalize("https://[::ffff:192.0.2.1]/");

        assertThat(result.ipLiteral()).isTrue();
        assertThat(result.registrableDomain()).isNull();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(
            resources = "/url-regression-corpus.csv", numLinesToSkip = 1, delimiterString = "|", encoding = "UTF-8")
    void enforcesCanonicalIpv4AndPreservesOtherHostParsing(String id, String url, String expected) {
        if ("REJECTED".equals(expected)) {
            assertThatThrownBy(() -> normalizer.normalize(url))
                    .as(id)
                    .isInstanceOf(InvalidUrlException.class);
            return;
        }

        NormalizedUrl result = normalizer.normalize(url);
        boolean expectedIpLiteral = switch (expected) {
            case "IPV4", "IPV6" -> true;
            case "HOSTNAME" -> false;
            default -> throw new AssertionError("Unknown regression outcome: " + expected);
        };
        assertThat(result.ipLiteral()).as(id).isEqualTo(expectedIpLiteral);
    }

    @Test
    void rejectsAnIpv6LiteralThatExceedsThePersistedHostLimit() {
        String oversizedZone = "a".repeat(253);

        assertInvalidUrl("https://[fe80::1%25" + oversizedZone + "]/login");
    }

    private void assertInvalidUrl(String input) {
        assertThatThrownBy(() -> normalizer.normalize(input))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageNotContaining("secret");
    }
}
