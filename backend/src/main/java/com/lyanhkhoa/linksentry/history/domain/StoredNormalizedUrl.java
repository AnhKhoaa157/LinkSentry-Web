package com.lyanhkhoa.linksentry.history.domain;

import java.util.Objects;

/** Safe normalized URL fields that are part of the public scan response. */
public record StoredNormalizedUrl(
        String scheme,
        String host,
        String asciiHost,
        String registrableDomain,
        Integer port,
        String path,
        boolean queryPresent,
        boolean fragmentPresent) {

    public StoredNormalizedUrl {
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(asciiHost, "asciiHost");
        Objects.requireNonNull(path, "path");
    }
}
