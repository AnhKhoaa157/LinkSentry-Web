/**
 * Individual {@link com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule}
 * implementations.
 *
 * <p>Exercise 4 fills it, one rule at a time:
 *
 * <ol>
 *   <li>{@code MISSING_HTTPS}
 *   <li>{@code IP_LITERAL_HOST}
 *   <li>{@code SPECIAL_USE_OR_PRIVATE_HOST}
 *   <li>{@code EXCESSIVE_URL_LENGTH}
 *   <li>{@code EXCESSIVE_SUBDOMAINS}
 *   <li>{@code SUSPICIOUS_KEYWORDS}
 *   <li>{@code BRAND_DOMAIN_MISMATCH}
 *   <li>{@code BRAND_LOOKALIKE_HOSTNAME}
 *   <li>{@code PUNYCODE_HOST}
 *   <li>{@code ENCODED_CHARACTERS}
 *   <li>{@code KNOWN_URL_SHORTENER}
 * </ol>
 *
 * <p>Conventions for this package:
 *
 * <ul>
 *   <li>One class per rule, one test class per rule. A rule small enough to share a
 *       file with another is small enough to be part of that other rule.
 *   <li>Thresholds and word lists come from configuration, never from a literal
 *       buried in an {@code if}. Shortener lists in particular go stale and must be
 *       changeable without a redeploy.
 *   <li>Rules are pure and stateless; a single instance serves concurrent requests.
 *   <li>Which URL component a rule inspects is a deliberate decision. {@code login}
 *       in a hostname is a strong signal; {@code login} in a path is not —
 *       {@code github.com/login} is legitimate.
 * </ul>
 */
package com.lyanhkhoa.linksentry.analysis.rules;
