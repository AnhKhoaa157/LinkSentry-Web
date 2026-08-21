package com.lyanhkhoa.linksentry.analysis.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IpAddressScopeClassifierTest {

    private final IpAddressScopeClassifier classifier = new IpAddressScopeClassifier();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "8.8.8.8, PUBLIC",
        "10.0.0.0, PRIVATE",
        "10.255.255.255, PRIVATE",
        "11.0.0.0, PUBLIC",
        "172.15.255.255, PUBLIC",
        "172.16.0.0, PRIVATE",
        "172.31.255.255, PRIVATE",
        "172.32.0.0, PUBLIC",
        "192.167.255.255, PUBLIC",
        "192.168.0.0, PRIVATE",
        "192.168.255.255, PRIVATE",
        "192.169.0.0, PUBLIC",
        "127.255.255.255, LOOPBACK",
        "128.0.0.0, PUBLIC",
        "169.253.255.255, PUBLIC",
        "169.254.0.0, LINK_LOCAL",
        "169.254.255.255, LINK_LOCAL",
        "192.0.0.1, SPECIAL_USE",
        "192.0.2.0, DOCUMENTATION",
        "192.0.3.0, PUBLIC",
        "100.64.0.0, SPECIAL_USE",
        "100.127.255.255, SPECIAL_USE",
        "100.128.0.0, PUBLIC",
        "198.18.0.0, SPECIAL_USE",
        "198.19.255.255, SPECIAL_USE",
        "198.20.0.0, PUBLIC",
        "198.51.100.1, DOCUMENTATION",
        "203.0.113.255, DOCUMENTATION",
        "224.0.0.1, SPECIAL_USE",
        "240.0.0.1, SPECIAL_USE"
    })
    void classifiesIpv4RangesAndBoundaries(String host, IpAddressScope expected) {
        assertThat(classifier.classify(host)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "'[2606:4700:4700::1111]', PUBLIC",
        "'[::]', SPECIAL_USE",
        "'[::1]', LOOPBACK",
        "'[fc00::]', UNIQUE_LOCAL",
        "'[fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff]', UNIQUE_LOCAL",
        "'[fe00::]', SPECIAL_USE",
        "'[fe80::]', LINK_LOCAL",
        "'[febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff]', LINK_LOCAL",
        "'[fec0::]', SPECIAL_USE",
        "'[2001:db8::]', DOCUMENTATION",
        "'[2001:db9::]', PUBLIC",
        "'[3fff::]', DOCUMENTATION",
        "'[64:ff9b::]', SPECIAL_USE",
        "'[100::]', SPECIAL_USE",
        "'[2001::]', SPECIAL_USE",
        "'[2002::]', SPECIAL_USE",
        "'[5f00::]', SPECIAL_USE",
        "'[ff00::]', SPECIAL_USE",
        "'[::ffff:8.8.8.8]', PUBLIC",
        "'[::ffff:10.0.0.1]', PRIVATE",
        "'[::ffff:192.0.2.1]', DOCUMENTATION",
        "'[fe80::1%25eth0]', LINK_LOCAL"
    })
    void classifiesIpv6RangesAndMappedIpv4(String host, IpAddressScope expected) {
        assertThat(classifier.classify(host)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"example.com", "017.0.0.1", "2001:db8::1", "[not-an-ip]"})
    void nonLiteralsAreHostnames(String host) {
        assertThat(classifier.classify(host)).isEqualTo(IpAddressScope.HOSTNAME);
    }
}
