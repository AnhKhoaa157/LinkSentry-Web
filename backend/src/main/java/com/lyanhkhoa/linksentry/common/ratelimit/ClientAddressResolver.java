package com.lyanhkhoa.linksentry.common.ratelimit;

import com.google.common.net.InetAddresses;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides which client address a rate-limit bucket belongs to.
 *
 * <p>By default this is the server-observed {@link HttpServletRequest#getRemoteAddr()}. Behind an
 * edge proxy, that address belongs to the proxy rather than the visitor, so every visitor would share
 * the same few buckets. When {@code linksentry.client-address.trusted-header} names a header that the
 * edge overwrites on every request (Cloudflare's {@code CF-Connecting-IP} on Render), buckets are
 * keyed on that header instead.
 *
 * <p>The header is used only when it appears exactly once and holds a single IPv4 or IPv6 literal.
 * Anything else falls back to {@code getRemoteAddr()}. Literals are parsed without any DNS lookup.
 * {@code X-Forwarded-For} and {@code Forwarded} are never read, because clients can append to them.
 *
 * <p>IPv6 addresses are keyed by their /64 prefix: one subscriber normally holds a whole /64 and
 * could otherwise rotate through it to reset its own buckets.
 *
 * <p>Addresses are never logged. Each distinct header outcome is logged once per process, so an
 * operator can confirm from the logs that the edge header actually arrives.
 */
public final class ClientAddressResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientAddressResolver.class);

    /** RFC 9110 token characters: what a header field name may consist of. */
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private static final Set<String> FORWARDING_CHAIN_HEADERS = Set.of("x-forwarded-for", "forwarded");

    /** Longest textual IPv6 form, e.g. {@code ffff:ffff:ffff:ffff:ffff:ffff:255.255.255.255}. */
    private static final int MAX_ADDRESS_LITERAL_LENGTH = 45;

    private final String trustedHeader;
    private final Set<Outcome> loggedOutcomes = ConcurrentHashMap.newKeySet();

    /**
     * @param trustedHeader the edge header to trust, or {@code null}/blank to key on the connection
     *                      address only
     * @throws IllegalArgumentException if the name is not a valid header name or is a forwarding-chain
     *                                  header, so a bad deployment setting fails at startup
     */
    public ClientAddressResolver(String trustedHeader) {
        if (trustedHeader == null || trustedHeader.isBlank()) {
            this.trustedHeader = null;
            log.info("Rate limits key on the connection address");
            return;
        }
        String name = trustedHeader.strip();
        if (!HEADER_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "linksentry.client-address.trusted-header must be a single HTTP header name");
        }
        if (FORWARDING_CHAIN_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("linksentry.client-address.trusted-header must not be " + name
                    + ": clients can append to forwarding-chain headers. Use a header the edge overwrites,"
                    + " such as CF-Connecting-IP.");
        }
        this.trustedHeader = name;
        log.info("Rate limits key on the {} header when it holds one IP literal, else the connection address", name);
    }

    /** Returns the bucket identity for {@code request}. Never null for a servlet-container request. */
    public String resolve(HttpServletRequest request) {
        String fromHeader = trustedHeader == null ? null : addressFromTrustedHeader(request);
        return bucketKey(fromHeader != null ? fromHeader : request.getRemoteAddr());
    }

    private String addressFromTrustedHeader(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(trustedHeader);
        if (values == null || !values.hasMoreElements()) {
            logOnce(Outcome.ABSENT);
            return null;
        }
        String value = values.nextElement();
        if (values.hasMoreElements() || !isSingleAddressLiteral(value)) {
            logOnce(Outcome.INVALID);
            return null;
        }
        logOnce(Outcome.USED);
        return value.strip();
    }

    private static boolean isSingleAddressLiteral(String value) {
        if (value == null) {
            return false;
        }
        String candidate = value.strip();
        // '%' would carry an IPv6 zone index, which has no meaning for a remote visitor.
        return !candidate.isEmpty()
                && candidate.length() <= MAX_ADDRESS_LITERAL_LENGTH
                && candidate.indexOf('%') < 0
                && InetAddresses.isInetAddress(candidate);
    }

    /**
     * Canonical bucket identity: the canonical text of an IPv4 address, or an IPv6 /64 prefix. A
     * value that is not an address literal is returned unchanged, matching the previous behaviour for
     * whatever the container reported.
     */
    static String bucketKey(String address) {
        if (address == null || address.indexOf('%') >= 0 || !InetAddresses.isInetAddress(address)) {
            return address;
        }
        InetAddress parsed = InetAddresses.forString(address);
        if (parsed instanceof Inet6Address) {
            byte[] bytes = parsed.getAddress();
            Arrays.fill(bytes, 8, bytes.length, (byte) 0);
            return InetAddresses.toAddrString(fromBytes(bytes)) + "/64";
        }
        return InetAddresses.toAddrString(parsed);
    }

    private static InetAddress fromBytes(byte[] bytes) {
        try {
            // getByAddress never performs a lookup; it only fails for an invalid length.
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException("A 16-byte IPv6 address is always valid", impossible);
        }
    }

    private void logOnce(Outcome outcome) {
        if (loggedOutcomes.add(outcome)) {
            log.info("Rate-limit client address header {}: {} (logged once per outcome; addresses are never logged)",
                    trustedHeader, outcome.description);
        }
    }

    private enum Outcome {
        USED("present with one IP literal, used as the client address"),
        ABSENT("absent, fell back to the connection address"),
        INVALID("present but not exactly one IP literal, fell back to the connection address");

        private final String description;

        Outcome(String description) {
            this.description = description;
        }
    }
}
