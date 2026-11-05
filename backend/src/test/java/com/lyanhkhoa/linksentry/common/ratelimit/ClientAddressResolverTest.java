package com.lyanhkhoa.linksentry.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {

    private static final String EDGE_HEADER = "CF-Connecting-IP";
    private static final String PROXY_ADDRESS = "10.0.0.5";

    @Test
    @DisplayName("without a trusted header the connection address is used, even if an edge header is sent")
    void defaultsToConnectionAddress() {
        MockHttpServletRequest request = request(PROXY_ADDRESS);
        request.addHeader(EDGE_HEADER, "203.0.113.10");

        assertThat(new ClientAddressResolver(null).resolve(request)).isEqualTo(PROXY_ADDRESS);
        assertThat(new ClientAddressResolver("   ").resolve(request)).isEqualTo(PROXY_ADDRESS);
    }

    @Test
    @DisplayName("one IPv4 literal in the trusted header becomes the client address")
    void usesTrustedHeaderAddress() {
        MockHttpServletRequest request = request(PROXY_ADDRESS);
        request.addHeader(EDGE_HEADER, " 203.0.113.10 ");

        assertThat(resolver().resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("the configured header name matches regardless of letter case")
    void headerNameIsCaseInsensitive() {
        MockHttpServletRequest request = request(PROXY_ADDRESS);
        request.addHeader(EDGE_HEADER, "203.0.113.10");

        assertThat(new ClientAddressResolver("cf-connecting-ip").resolve(request)).isEqualTo("203.0.113.10");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "example.com",
        "203.0.113.10, 198.51.100.20",
        "999.1.1.1",
        "203.0.113.10:443",
        "fe80::1%eth0",
        "unknown",
        "<script>",
        "ffff:ffff:ffff:ffff:ffff:ffff:255.255.255.255:extra-text"
    })
    @DisplayName("anything other than one IP literal falls back to the connection address")
    void invalidHeaderValuesFallBack(String value) {
        MockHttpServletRequest request = request(PROXY_ADDRESS);
        request.addHeader(EDGE_HEADER, value);

        assertThat(resolver().resolve(request)).isEqualTo(PROXY_ADDRESS);
    }

    @Test
    @DisplayName("a repeated trusted header falls back to the connection address")
    void repeatedHeaderFallsBack() {
        MockHttpServletRequest request = request(PROXY_ADDRESS);
        request.addHeader(EDGE_HEADER, "203.0.113.10");
        request.addHeader(EDGE_HEADER, "198.51.100.20");

        assertThat(resolver().resolve(request)).isEqualTo(PROXY_ADDRESS);
    }

    @Test
    @DisplayName("an absent trusted header falls back, and forwarding-chain headers are never read")
    void absentHeaderFallsBackAndForwardingChainIsIgnored() {
        MockHttpServletRequest request = request(PROXY_ADDRESS);
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        request.addHeader("Forwarded", "for=203.0.113.10");

        assertThat(resolver().resolve(request)).isEqualTo(PROXY_ADDRESS);
    }

    @Test
    @DisplayName("IPv6 visitors are keyed by their /64 prefix in canonical form")
    void ipv6IsKeyedBySlash64() {
        assertThat(resolveHeader("2001:0DB8:0001:0002:0003:0004:0005:0006")).isEqualTo("2001:db8:1:2::/64");
        assertThat(resolveHeader("2001:db8:1:2:ffff::1")).isEqualTo("2001:db8:1:2::/64");
        assertThat(resolveHeader("2001:db8:1:3::1")).isEqualTo("2001:db8:1:3::/64");
    }

    @Test
    @DisplayName("an IPv4-mapped IPv6 literal is keyed as the plain IPv4 address")
    void ipv4MappedAddressIsKeyedAsIpv4() {
        assertThat(resolveHeader("::ffff:203.0.113.10")).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("an IPv6 connection address is also keyed by /64 when no header is configured")
    void ipv6ConnectionAddressIsKeyedBySlash64() {
        assertThat(new ClientAddressResolver(null).resolve(request("2001:db8:1:2:3:4:5:6")))
                .isEqualTo("2001:db8:1:2::/64");
    }

    @Test
    @DisplayName("a connection address that is not an IP literal is passed through unchanged")
    void unparseableConnectionAddressPassesThrough() {
        assertThat(new ClientAddressResolver(null).resolve(request("not-an-address"))).isEqualTo("not-an-address");
    }

    @Test
    @DisplayName("forwarding-chain headers and malformed header names are refused at startup")
    void refusesUnsafeHeaderNames() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientAddressResolver("X-Forwarded-For"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientAddressResolver("forwarded"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientAddressResolver("CF Connecting IP"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClientAddressResolver("CF-Connecting-IP:"));
    }

    private static ClientAddressResolver resolver() {
        return new ClientAddressResolver(EDGE_HEADER);
    }

    private static String resolveHeader(String value) {
        MockHttpServletRequest request = request(PROXY_ADDRESS);
        request.addHeader(EDGE_HEADER, value);
        return resolver().resolve(request);
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/scans");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
