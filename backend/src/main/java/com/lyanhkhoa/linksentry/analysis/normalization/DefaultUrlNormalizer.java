package com.lyanhkhoa.linksentry.analysis.normalization;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.net.IDN;
import java.net.URI;
import java.util.Locale;

public final class DefaultUrlNormalizer implements UrlNormalizer {

    private static final int MAX_HOST_LENGTH = 253;
    private static final int MAX_PORT = 65_535;

    private final PublicSuffixDomainResolver domainResolver =
            new PublicSuffixDomainResolver();

    @Override
    public NormalizedUrl normalize(String rawInput) {
        validateRawInput(rawInput);
        URI uri = parseUri(rawInput);
        ParsedAuthority authority = validateUri(uri);

        return toNormalizedUrl(uri, rawInput, authority);
    }

    private void validateRawInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new InvalidUrlException("Invalid URL input");
        }

        if (rawInput.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL input exceeds maximum length");
        }
    }

    private URI parseUri(String rawInput) {
        try {
            return URI.create(rawInput);
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException("Malformed URL input", e);
        }
    }

    private ParsedAuthority validateUri(URI uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new InvalidUrlException("Only http and https are supported");
        }

        ParsedAuthority authority = parseAuthority(uri);
        String host = normalizeHost(authority.host());
        if (!isIpLiteral(host)) {
            try {
                String asciiHost = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
                validateAsciiHost(asciiHost);
            } catch (IllegalArgumentException exception) {
                throw new InvalidUrlException("URL must have a valid host", exception);
            }
        }

        return new ParsedAuthority(host, authority.port());
    }

    private ParsedAuthority parseAuthority(URI uri) {
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isBlank() || authority.indexOf('@') >= 0) {
            throw new InvalidUrlException("URL must have a valid host");
        }

        if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            if (closingBracket < 0) {
                throw new InvalidUrlException("URL must have a valid host");
            }
            return new ParsedAuthority(
                    authority.substring(0, closingBracket + 1),
                    parsePortSuffix(authority.substring(closingBracket + 1)));
        }

        int colonIndex = authority.lastIndexOf(':');
        if (colonIndex < 0) {
            return new ParsedAuthority(authority, null);
        }

        if (authority.indexOf(':') != colonIndex) {
            throw new InvalidUrlException("URL must have a valid host.");
        }

        String host = authority.substring(0, colonIndex);
        if (host.isBlank()) {
            throw new InvalidUrlException("URL must have a valid host.");
        }

        return new ParsedAuthority(host, parsePortSuffix(authority.substring(colonIndex)));
    }

    private Integer parsePortSuffix(String suffix) {
        if (suffix.isEmpty()) {
            return null;
        }
        String portValue = suffix.startsWith(":") ? suffix.substring(1) : "";
        if (portValue.isEmpty() || !portValue.chars().allMatch(this::isAsciiDigit)) {
            throw new InvalidUrlException("URL must have a valid port");
        }

        try {
            int port = Integer.parseInt(portValue);
            if (port > MAX_PORT) {
                throw new InvalidUrlException("URL must have a valid port");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new InvalidUrlException("URL must have a valid port", exception);
        }
    }

    private NormalizedUrl toNormalizedUrl(URI uri, String rawInput, ParsedAuthority authority) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = authority.host();
        boolean ipLiteral = isIpLiteral(host);
        String asciiHost = ipLiteral
            ? host
            : toAsciiHost(host);
        DomainParts domainParts = ipLiteral
            ? DomainParts.withoutRegistrableDomain()
            : resolveDomain(asciiHost);
        Integer port = authority.port();
        String path = (uri.getRawPath() == null ? "" : uri.getRawPath());

        String redactedDisplayValue = scheme + "://" + host + (port == null ? "" : ":" + port) + path;

        return new NormalizedUrl(
                rawInput,
                redactedDisplayValue,
                scheme,
                host,
                asciiHost,
                domainParts.registrableDomain(),
                domainParts.subdomains(),
                port,
                path,
                uri.getRawQuery() != null,
                uri.getRawFragment() != null,
                ipLiteral
            );
    }

    private String toAsciiHost(String host) {
        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new InvalidUrlException("URL must have a valid host", exception);
        }
    }

    private DomainParts resolveDomain(String asciiHost) {
        try {
            return domainResolver.resolve(asciiHost);
        } catch (IllegalArgumentException exception) {
            throw new InvalidUrlException("URL must have a valid host", exception);
        }
    }

    private String normalizeHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty()) {
            throw new InvalidUrlException("URL must have a valid host");
        }

        if (host.startsWith("[") || host.endsWith("]")) {
            if (!isValidIpv6Literal(host) || host.length() > MAX_HOST_LENGTH) {
                throw new InvalidUrlException("URL must have a valid host");
            }
            return host;
        }

        if (looksLikeMalformedIpv4(host) && !isValidIpv4Literal(host)) {
            throw new InvalidUrlException("URL must have a valid host");
        }
        return host;
    }

    private void validateAsciiHost(String asciiHost) {
        if (asciiHost.length() > MAX_HOST_LENGTH) {
            throw new IllegalArgumentException("host is too long");
        }

        String[] labels = asciiHost.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")) {
                throw new IllegalArgumentException("host contains an invalid label");
            }
        }
    }

    private boolean isIpLiteral(String host) {
        return isValidIpv4Literal(host) || isValidIpv6Literal(host);
    }

    private boolean looksLikeMalformedIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        return parts.length == 4 && java.util.Arrays.stream(parts)
                .allMatch(part -> !part.isEmpty() && part.chars().allMatch(this::isAsciiDigit));
    }

    private boolean isValidIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4 || !java.util.Arrays.stream(parts)
                .allMatch(part -> !part.isEmpty() && part.chars().allMatch(this::isAsciiDigit))) {
            return false;
        }

        for (String part : parts) {
            if (part.length() > 3 || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidIpv6Literal(String host) {
        if (!host.startsWith("[") || !host.endsWith("]")) {
            return false;
        }

        String address = host.substring(1, host.length() - 1);
        int zoneIndex = address.indexOf("%25");
        if (zoneIndex >= 0) {
            String zone = address.substring(zoneIndex + 3);
            if (zone.isEmpty() || !zone.chars().allMatch(this::isValidZoneCharacter)) {
                return false;
            }
            address = address.substring(0, zoneIndex);
        }
        if (address.isEmpty() || address.indexOf('%') >= 0) {
            return false;
        }

        int compressionIndex = address.indexOf("::");
        if (compressionIndex >= 0 && address.indexOf("::", compressionIndex + 2) >= 0) {
            return false;
        }

        String left = compressionIndex >= 0 ? address.substring(0, compressionIndex) : address;
        String right = compressionIndex >= 0 ? address.substring(compressionIndex + 2) : "";
        int groups = countIpv6Groups(left) + countIpv6Groups(right);
        if (groups < 0) {
            return false;
        }
        return compressionIndex >= 0 ? groups < 8 : groups == 8;
    }

    private int countIpv6Groups(String side) {
        if (side.isEmpty()) {
            return 0;
        }

        String[] groups = side.split(":", -1);
        int count = 0;
        for (int index = 0; index < groups.length; index++) {
            String group = groups[index];
            if (group.isEmpty()) {
                return -1;
            }
            if (group.indexOf('.') >= 0) {
                if (index != groups.length - 1 || !isValidIpv4Literal(group)) {
                    return -1;
                }
                count += 2;
            } else if (group.length() <= 4 && group.chars().allMatch(this::isHexDigit)) {
                count++;
            } else {
                return -1;
            }
        }
        return count;
    }

    private boolean isAsciiDigit(int value) {
        return value >= '0' && value <= '9';
    }

    private boolean isHexDigit(int value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }

    private boolean isValidZoneCharacter(int value) {
        return Character.isLetterOrDigit(value) || value == '.' || value == '_' || value == '-';
    }

    private record ParsedAuthority(String host, Integer port) {
    }

}
