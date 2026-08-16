package com.lyanhkhoa.linksentry.scan.api;

import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;

/**
 * Wire representation of {@link NormalizedUrl}.
 *
 * <p>A separate type on purpose: it exposes only the fields documented in
 * {@code docs/API_CONTRACT.md} and never {@code originalInput}, which must never
 * leave the server.
 *
 * @param scheme            lowercase scheme, {@code http} or {@code https}
 * @param host              lowercase hostname as submitted
 * @param asciiHost         IDNA/Punycode ASCII form of {@code host}
 * @param registrableDomain the domain actually registered, or {@code null}
 * @param port              explicit port, or {@code null} when absent
 * @param path              the path component
 * @param queryPresent      whether a query string exists, never its content
 * @param fragmentPresent   whether a fragment exists
 */
public record NormalizedUrlResponse(
        String scheme,
        String host,
        String asciiHost,
        String registrableDomain,
        Integer port,
        String path,
        boolean queryPresent,
        boolean fragmentPresent) {

    public static NormalizedUrlResponse from(NormalizedUrl url) {
        return new NormalizedUrlResponse(
                url.scheme(),
                url.host(),
                url.asciiHost(),
                url.registrableDomain(),
                url.port(),
                url.path(),
                url.queryPresent(),
                url.fragmentPresent());
    }
}
