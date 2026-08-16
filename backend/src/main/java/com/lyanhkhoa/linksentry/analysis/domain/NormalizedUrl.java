package com.lyanhkhoa.linksentry.analysis.domain;

import java.util.List;
import java.util.Objects;

/**
 * The safe, canonical representation of a submitted URL.
 *
 * <p>Built exactly once per scan by a
 * {@link com.lyanhkhoa.linksentry.analysis.normalization.UrlNormalizer}, then read
 * by every {@link AnalysisRule}. Rules receive this and nothing else, which is
 * what guarantees they cannot perform network access: there is no host to connect
 * to here, only strings that have already been parsed.
 *
 * <p><strong>Contract for implementers.</strong> Constructing this type must never
 * resolve DNS, open a connection, or otherwise touch the network — see
 * {@code docs/SECURITY_BOUNDARY.md}.
 *
 * <p><strong>This field list is a starting point.</strong> Expect to revise it
 * while implementing the normalizer (Exercise 2); it is not a frozen contract.
 *
 * @param originalInput        the raw submitted string, retained for auditing only.
 *                             Never log it and never send it to a client — use
 *                             {@code redactedDisplayValue} for both.
 * @param redactedDisplayValue the only representation safe to render, persist or
 *                             quote as evidence. Embedded credentials and
 *                             sensitive query values must already be removed.
 * @param scheme               lowercase scheme; only {@code http} or {@code https}
 *                             reach this point
 * @param host                 lowercase hostname as submitted
 * @param asciiHost            IDNA/Punycode ASCII form of {@code host}, so rules
 *                             compare ASCII against ASCII
 * @param registrableDomain    the domain actually registered, or {@code null} when
 *                             there is none (an IP literal, or a bare public
 *                             suffix). Deriving this needs the Public Suffix List,
 *                             not the last two labels — see Exercise 3.
 * @param subdomains           labels to the left of {@code registrableDomain}, in
 *                             source order. Attacker-controlled: this is where a
 *                             deceptive brand name usually hides.
 * @param port                 explicit port, or {@code null} when absent
 * @param path                 the path component, case preserved (paths are
 *                             case-sensitive even though hosts are not)
 * @param queryPresent         whether a query string exists. Only the fact, never
 *                             the contents: query strings routinely carry tokens.
 * @param fragmentPresent      whether a fragment exists
 * @param ipLiteral            whether the host is an IP address rather than a name
 */
public record NormalizedUrl(
        String originalInput,
        String redactedDisplayValue,
        String scheme,
        String host,
        String asciiHost,
        String registrableDomain,
        List<String> subdomains,
        Integer port,
        String path,
        boolean queryPresent,
        boolean fragmentPresent,
        boolean ipLiteral) {

    /**
     * Enforces only the invariants the scaffold actually relies on: the identifying
     * strings are present, and {@code subdomains} is an immutable copy so a rule
     * cannot mutate state shared with every other rule.
     *
     * <p>Add further invariants as the normalizer takes shape — for example that
     * {@code scheme} is one of two values, or that {@code asciiHost} contains no
     * non-ASCII characters.
     */
    public NormalizedUrl {
        Objects.requireNonNull(originalInput, "originalInput");
        Objects.requireNonNull(redactedDisplayValue, "redactedDisplayValue");
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(asciiHost, "asciiHost");
        subdomains = subdomains == null ? List.of() : List.copyOf(subdomains);
    }

    /** How many subdomain labels sit left of the registrable domain. */
    public int subdomainDepth() {
        return subdomains.size();
    }
}
