package com.lyanhkhoa.linksentry.analysis.normalization;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;
import java.net.IDN;
import java.net.URI;
import java.util.Locale;

public class DefaultUrlNormalizer implements UrlNormalizer {

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
        if (uri.getRawUserInfo() != null || uri.getRawAuthority().contains("@")) {
            throw new InvalidUrlException("URLs with embedded credentials are not supported.");
        }

        if (!isIpLiteral(authority.host())) {
            try {
                IDN.toASCII(authority.host(), IDN.USE_STD3_ASCII_RULES);
            } catch (IllegalArgumentException exception) {
                throw new InvalidUrlException("URL must have a valid host.", exception);
            }
        }

        return authority;
    }

    private ParsedAuthority parseAuthority(URI uri) {
        String parsedHost = uri.getHost();
        if (parsedHost != null) {
            return new ParsedAuthority(parsedHost, uri.getPort() == -1 ? null : uri.getPort());
        }

        String authority = uri.getRawAuthority();
        if (authority == null || authority.isBlank()) {
            throw new InvalidUrlException("URL must have a valid host.");
        }

        int colonIndex = authority.lastIndexOf(':');
        if (colonIndex < 0) {
            return new ParsedAuthority(authority, null);
        }

        if (authority.indexOf(':') != colonIndex) {
            throw new InvalidUrlException("URL must have a valid host.");
        }

        String host = authority.substring(0, colonIndex);
        String portValue = authority.substring(colonIndex + 1);
        if (host.isBlank() || !portValue.matches("\\d+")) {
            throw new InvalidUrlException("URL must have a valid host.");
        }

        try {
            return new ParsedAuthority(host, Integer.valueOf(portValue));
        } catch (NumberFormatException exception) {
            throw new InvalidUrlException("URL must have a valid host.", exception);
        }
    }

    private NormalizedUrl toNormalizedUrl(URI uri, String rawInput, ParsedAuthority authority) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = authority.host().toLowerCase(Locale.ROOT);
        boolean ipLiteral = isIpLiteral(host);
        String asciiHost = ipLiteral
            ? host
            : IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
        DomainParts domainParts = ipLiteral
            ? DomainParts.withoutRegistrableDomain()
            : domainResolver.resolve(asciiHost);
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

    private boolean isIpLiteral(String host) {
        return isIpv4Literal(host) || isIpv6Literal(host);
    }

    private boolean isIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);

        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (part.isEmpty() || value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6Literal(String host) {
        // Simplified IPv6 validation - a full implementation would be more complex
        return host.startsWith("[") && host.endsWith("]");
    }

    private record ParsedAuthority(String host, Integer port) {
    }

}
