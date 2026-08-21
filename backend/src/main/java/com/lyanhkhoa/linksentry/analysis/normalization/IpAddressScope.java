package com.lyanhkhoa.linksentry.analysis.normalization;

/** A deterministic classification of a normalized host's IP-address scope. */
public enum IpAddressScope {
    HOSTNAME("hostname"),
    PUBLIC("public IP address"),
    PRIVATE("private IP address"),
    LOOPBACK("loopback address"),
    LINK_LOCAL("link-local address"),
    UNIQUE_LOCAL("unique-local IPv6 address"),
    DOCUMENTATION("documentation address"),
    SPECIAL_USE("special-use address");

    private final String evidence;

    IpAddressScope(String evidence) {
        this.evidence = evidence;
    }

    public boolean isPublicIpLiteral() {
        return this == PUBLIC;
    }

    public boolean isSpecialUseOrPrivateIpLiteral() {
        return this != HOSTNAME && this != PUBLIC;
    }

    public String evidence() {
        return "Host scope: " + evidence;
    }
}
