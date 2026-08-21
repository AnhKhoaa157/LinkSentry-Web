package com.lyanhkhoa.linksentry.analysis.normalization;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Classifies literal IP hosts with a fixed, local range table.
 *
 * <p>The selected prefixes come from the IANA IPv4 and IPv6 Special-Purpose
 * Address Registries, revision 2025-10-09, plus multicast and deprecated
 * site-local address space. This is a lexical classifier, not a routability or
 * reachability oracle: it performs no DNS, socket, file, or other I/O, and an
 * address outside the selected ranges is simply {@link IpAddressScope#PUBLIC}.
 */
public final class IpAddressScopeClassifier {

    public static final String TABLE_VERSION = "iana-special-purpose-2025-10-09";

    private static final List<Ipv4Prefix> IPV4_PREFIXES = List.of(
            ipv4("0.0.0.0", 8, IpAddressScope.SPECIAL_USE),
            ipv4("10.0.0.0", 8, IpAddressScope.PRIVATE),
            ipv4("100.64.0.0", 10, IpAddressScope.SPECIAL_USE),
            ipv4("127.0.0.0", 8, IpAddressScope.LOOPBACK),
            ipv4("169.254.0.0", 16, IpAddressScope.LINK_LOCAL),
            ipv4("172.16.0.0", 12, IpAddressScope.PRIVATE),
            ipv4("192.0.0.0", 24, IpAddressScope.SPECIAL_USE),
            ipv4("192.0.2.0", 24, IpAddressScope.DOCUMENTATION),
            ipv4("192.88.99.0", 24, IpAddressScope.SPECIAL_USE),
            ipv4("192.168.0.0", 16, IpAddressScope.PRIVATE),
            ipv4("198.18.0.0", 15, IpAddressScope.SPECIAL_USE),
            ipv4("198.51.100.0", 24, IpAddressScope.DOCUMENTATION),
            ipv4("203.0.113.0", 24, IpAddressScope.DOCUMENTATION),
            ipv4("224.0.0.0", 4, IpAddressScope.SPECIAL_USE),
            ipv4("240.0.0.0", 4, IpAddressScope.SPECIAL_USE));

    private static final List<Ipv6Prefix> IPV6_PREFIXES = List.of(
            ipv6("::", 128, IpAddressScope.SPECIAL_USE),
            ipv6("::1", 128, IpAddressScope.LOOPBACK),
            ipv6("64:ff9b::", 96, IpAddressScope.SPECIAL_USE),
            ipv6("64:ff9b:1::", 48, IpAddressScope.SPECIAL_USE),
            ipv6("100::", 64, IpAddressScope.SPECIAL_USE),
            ipv6("100:0:0:1::", 64, IpAddressScope.SPECIAL_USE),
            ipv6("2001::", 23, IpAddressScope.SPECIAL_USE),
            ipv6("2001:db8::", 32, IpAddressScope.DOCUMENTATION),
            ipv6("2002::", 16, IpAddressScope.SPECIAL_USE),
            ipv6("3fff::", 20, IpAddressScope.DOCUMENTATION),
            ipv6("5f00::", 16, IpAddressScope.SPECIAL_USE),
            ipv6("fc00::", 7, IpAddressScope.UNIQUE_LOCAL),
            ipv6("fe00::", 9, IpAddressScope.SPECIAL_USE),
            ipv6("fe80::", 10, IpAddressScope.LINK_LOCAL),
            ipv6("fec0::", 10, IpAddressScope.SPECIAL_USE),
            ipv6("ff00::", 8, IpAddressScope.SPECIAL_USE));

    /**
     * Returns the literal scope, or {@link IpAddressScope#HOSTNAME} when {@code host}
     * is not a canonical IPv4 literal or bracketed IPv6 literal.
     */
    public IpAddressScope classify(String host) {
        Objects.requireNonNull(host, "host");

        Integer ipv4 = parseIpv4(host);
        if (ipv4 != null) {
            return classifyIpv4(ipv4);
        }

        Ipv6Address ipv6 = parseBracketedIpv6(host);
        if (ipv6 == null) {
            return IpAddressScope.HOSTNAME;
        }
        if (ipv6.isIpv4Mapped()) {
            return classifyIpv4((int) ipv6.low());
        }

        return IPV6_PREFIXES.stream()
                .filter(prefix -> prefix.contains(ipv6))
                .map(Ipv6Prefix::scope)
                .findFirst()
                .orElse(IpAddressScope.PUBLIC);
    }

    private static IpAddressScope classifyIpv4(int address) {
        return IPV4_PREFIXES.stream()
                .filter(prefix -> prefix.contains(address))
                .map(Ipv4Prefix::scope)
                .findFirst()
                .orElse(IpAddressScope.PUBLIC);
    }

    private static Ipv4Prefix ipv4(String address, int prefixLength, IpAddressScope scope) {
        Integer parsed = parseIpv4(address);
        if (parsed == null) {
            throw new IllegalStateException("Invalid IPv4 range table entry");
        }
        return new Ipv4Prefix(parsed, prefixLength, scope);
    }

    private static Ipv6Prefix ipv6(String address, int prefixLength, IpAddressScope scope) {
        Ipv6Address parsed = parseIpv6(address);
        if (parsed == null) {
            throw new IllegalStateException("Invalid IPv6 range table entry");
        }
        return new Ipv6Prefix(parsed.high(), parsed.low(), prefixLength, scope);
    }

    private static Integer parseIpv4(String address) {
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }

        int result = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3
                    || (part.length() > 1 && part.charAt(0) == '0')) {
                return null;
            }

            int octet = 0;
            for (int index = 0; index < part.length(); index++) {
                int character = part.charAt(index);
                if (character < '0' || character > '9') {
                    return null;
                }
                octet = octet * 10 + character - '0';
            }
            if (octet > 255) {
                return null;
            }
            result = (result << 8) | octet;
        }
        return result;
    }

    private static Ipv6Address parseBracketedIpv6(String host) {
        if (!host.startsWith("[") || !host.endsWith("]")) {
            return null;
        }

        String address = host.substring(1, host.length() - 1);
        int zoneIndex = address.indexOf("%25");
        if (zoneIndex >= 0) {
            String zone = address.substring(zoneIndex + 3);
            if (zone.isEmpty() || !zone.chars().allMatch(IpAddressScopeClassifier::isZoneCharacter)) {
                return null;
            }
            address = address.substring(0, zoneIndex);
        }
        if (address.isEmpty() || address.indexOf('%') >= 0) {
            return null;
        }
        return parseIpv6(address);
    }

    private static Ipv6Address parseIpv6(String address) {
        int compressionIndex = address.indexOf("::");
        if (compressionIndex >= 0 && address.indexOf("::", compressionIndex + 2) >= 0) {
            return null;
        }

        String left = compressionIndex >= 0 ? address.substring(0, compressionIndex) : address;
        String right = compressionIndex >= 0 ? address.substring(compressionIndex + 2) : "";
        Ipv6Groups leftGroups = parseIpv6Groups(left);
        Ipv6Groups rightGroups = parseIpv6Groups(right);
        if (leftGroups == null || rightGroups == null || leftGroups.hasIpv4Tail() && compressionIndex >= 0) {
            return null;
        }

        int providedGroups = leftGroups.values().size() + rightGroups.values().size();
        int omittedGroups = compressionIndex >= 0 ? 8 - providedGroups : 0;
        if (providedGroups > 8 || compressionIndex >= 0 && omittedGroups == 0 || compressionIndex < 0 && providedGroups != 8) {
            return null;
        }

        int[] groups = new int[8];
        int position = 0;
        for (int group : leftGroups.values()) {
            groups[position++] = group;
        }
        position += omittedGroups;
        for (int group : rightGroups.values()) {
            groups[position++] = group;
        }

        long high = 0;
        long low = 0;
        for (int index = 0; index < 4; index++) {
            high = (high << 16) | groups[index];
        }
        for (int index = 4; index < 8; index++) {
            low = (low << 16) | groups[index];
        }
        return new Ipv6Address(high, low);
    }

    private static Ipv6Groups parseIpv6Groups(String side) {
        if (side.isEmpty()) {
            return new Ipv6Groups(List.of(), false);
        }

        String[] rawGroups = side.split(":", -1);
        List<Integer> groups = new ArrayList<>(rawGroups.length);
        boolean hasIpv4Tail = false;
        for (int index = 0; index < rawGroups.length; index++) {
            String group = rawGroups[index];
            if (group.isEmpty()) {
                return null;
            }

            if (group.indexOf('.') >= 0) {
                Integer ipv4 = parseIpv4(group);
                if (ipv4 == null || index != rawGroups.length - 1) {
                    return null;
                }
                groups.add((ipv4 >>> 16) & 0xffff);
                groups.add(ipv4 & 0xffff);
                hasIpv4Tail = true;
                continue;
            }

            if (group.length() > 4) {
                return null;
            }
            int value = 0;
            for (int character : group.toCharArray()) {
                int digit = asciiHexValue(character);
                if (digit < 0) {
                    return null;
                }
                value = value * 16 + digit;
            }
            groups.add(value);
        }
        return new Ipv6Groups(List.copyOf(groups), hasIpv4Tail);
    }

    private static boolean isZoneCharacter(int value) {
        return Character.isLetterOrDigit(value) || value == '.' || value == '_' || value == '-';
    }

    private static int asciiHexValue(int value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return -1;
    }

    private record Ipv4Prefix(int network, int prefixLength, IpAddressScope scope) {

        private boolean contains(int address) {
            return prefixLength == 0
                    || (address >>> (32 - prefixLength)) == (network >>> (32 - prefixLength));
        }
    }

    private record Ipv6Prefix(long high, long low, int prefixLength, IpAddressScope scope) {

        private boolean contains(Ipv6Address address) {
            if (prefixLength <= 64) {
                return highPrefixMatches(address.high(), high, prefixLength);
            }
            return address.high() == high && highPrefixMatches(address.low(), low, prefixLength - 64);
        }
    }

    private static boolean highPrefixMatches(long value, long prefix, int bitCount) {
        return bitCount == 0 || (value >>> (64 - bitCount)) == (prefix >>> (64 - bitCount));
    }

    private record Ipv6Address(long high, long low) {

        private boolean isIpv4Mapped() {
            return high == 0 && (low >>> 32) == 0x0000ffffL;
        }
    }

    private record Ipv6Groups(List<Integer> values, boolean hasIpv4Tail) {
    }
}
