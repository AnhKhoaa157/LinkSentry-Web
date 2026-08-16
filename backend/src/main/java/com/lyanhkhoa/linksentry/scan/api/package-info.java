/**
 * HTTP boundary of the scan feature.
 *
 * <p><strong>Empty by design.</strong> Exercise 7 adds {@code ScanController} for
 * {@code POST /api/v1/scans} plus its request and response DTOs, against the
 * contract in {@code docs/API_CONTRACT.md}.
 *
 * <p>Rules for this package:
 *
 * <ul>
 *   <li>DTOs are <em>separate types</em> from the domain records. Do not serialise
 *       {@link com.lyanhkhoa.linksentry.analysis.domain.NormalizedUrl} directly: the
 *       wire format and the domain model change for different reasons, and
 *       {@code originalInput} must never leave the server.
 *   <li>The controller validates, delegates to the application service, and maps the
 *       result. No analysis logic here.
 *   <li><strong>Never log the submitted URL</strong> — not at debug level, not
 *       temporarily. Log the scan id and the rule ids that fired.
 * </ul>
 */
package com.lyanhkhoa.linksentry.scan.api;
