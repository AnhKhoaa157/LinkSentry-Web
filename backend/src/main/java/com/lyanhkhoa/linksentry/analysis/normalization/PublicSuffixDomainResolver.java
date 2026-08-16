package com.lyanhkhoa.linksentry.analysis.normalization;

import com.google.common.net.InternetDomainName;
import java.util.List;
import java.util.Objects;

/** Resolves registrable domains using Guava's bundled Public Suffix List. */
public final class PublicSuffixDomainResolver {

    public DomainParts resolve(String asciiHost) {
        Objects.requireNonNull(asciiHost, "asciiHost");
        String host = asciiHost.endsWith(".")
                ? asciiHost.substring(0, asciiHost.length() - 1)
                : asciiHost;
        if (host.isEmpty() || host.startsWith("[") || isIpv4Literal(host)) {
            return DomainParts.withoutRegistrableDomain();
        }

        InternetDomainName domain = InternetDomainName.from(host);

        if (!domain.hasPublicSuffix() || !domain.isUnderPublicSuffix()) {
            return DomainParts.withoutRegistrableDomain();
        }

        InternetDomainName registrable = domain.topPrivateDomain();
        List<String> hostLabels = domain.parts();
        List<String> registrableParts = registrable.parts();

        int subdomainCount = hostLabels.size() - registrableParts.size();
        List<String> subdomains = hostLabels.subList(0, subdomainCount);

        return new DomainParts(registrable.toString(), subdomains);
    }

    private boolean isIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(this::isAsciiDigit)
                    || Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isAsciiDigit(int value) {
        return value >= '0' && value <= '9';
    }
}
