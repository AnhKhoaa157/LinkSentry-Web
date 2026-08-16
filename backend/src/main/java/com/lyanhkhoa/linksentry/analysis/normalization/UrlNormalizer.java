package com.lyanhkhoa.linksentry.analysis.normalization;

import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl;

/**
 * Converts raw user input into a safe canonical {@link NormalizedUrl}.
 *
 * <p>This is the implementation boundary for Exercises 1–3, and it is the most
 * important code in the product: every rule downstream trusts whatever this
 * produces.
 *
 * <p>Invariants an implementation must uphold:
 *
 * <ul>
 *   <li><strong>Validate before parsing.</strong> Reject blank input and input over
 *       the maximum length first. A parser is the wrong place to discover the input
 *       is megabytes long.
 *   <li><strong>{@code http} and {@code https} only.</strong> {@code javascript:},
 *       {@code data:}, {@code file:} and {@code ftp:} are rejected.
 *   <li><strong>Never touch the network.</strong> No DNS resolution, no connection,
 *       no redirect following — see {@code docs/SECURITY_BOUNDARY.md}.
 *   <li><strong>Case rules differ per component.</strong> Scheme and host are
 *       lowercased; the path is not, because paths are case-sensitive.
 *   <li><strong>Produce a redacted display value.</strong> It is the only
 *       representation the UI, the logs and the database may ever see.
 *   <li><strong>Deterministic.</strong> Same input, same output.
 *   <li><strong>Canonical host.</strong> Hosts are lowercased; a trailing DNS dot
 *       is accepted and removed, while embedded credentials are rejected.
 *   <li><strong>Explicit ports are preserved.</strong> This includes the default
 *       ports {@code 80} and {@code 443}; the display value keeps the submitted
 *       connection target visible.
 * </ul>
 *
 * <p>Two traps worth knowing before you start: {@code java.net.URI} accepts plenty
 * of strings you must still reject ({@code URI.create("foo")} yields a URI with a
 * null host rather than throwing), and the registrable domain cannot be derived by
 * taking the last two labels of the hostname — {@code co.uk} and {@code com.vn} are
 * public suffixes. Exercise 3 covers why.
 */
public interface UrlNormalizer {

    /** Maximum accepted input length, enforced before any parsing. */
    int MAX_URL_LENGTH = 2048;

    /**
     * Normalises raw submitted input.
     *
     * @param rawInput the raw string as submitted; may be blank or malformed
     * @return the canonical representation
     * @throws InvalidUrlException when the input is blank, too long, malformed, or
     *                             uses an unsupported scheme. The message must not
     *                             quote the offending input.
     */
    NormalizedUrl normalize(String rawInput);

    
}
