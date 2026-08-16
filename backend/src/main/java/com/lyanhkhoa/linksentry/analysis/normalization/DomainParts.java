package com.lyanhkhoa.linksentry.analysis.normalization;

import java.util.List;

/**
 * The Public Suffix List-derived components of a hostname.
 *
 * @param registrableDomain the registrable domain in ASCII form, or {@code
null}
 *                           when the host is an IP literal or bare public
suffix
 * @param subdomains labels to the left of the registrable domain, in source
order
 */

public record DomainParts(
        String registrableDomain,
        List<String> subdomains) {

    public DomainParts {
        subdomains = subdomains == null ? List.of() : List.copyOf(subdomains);

        if (registrableDomain == null && !subdomains.isEmpty()) {
            throw new IllegalArgumentException(
                    "Subdomains require a registrable domain");
        }

        if (registrableDomain != null && registrableDomain.isEmpty()) {
            throw new IllegalArgumentException(
                    "registrableDomain must be null or non-empty");
        }
    }

    public static DomainParts withoutRegistrableDomain() {
        return new DomainParts(null, List.of());
    }
}
