/**
 * Input validation and URL normalisation.
 *
 * <p>Currently holds the {@link
 * com.lyanhkhoa.linksentry.analysis.normalization.UrlNormalizer} contract and no
 * implementation. Exercises 1–3 of {@code docs/MANUAL_IMPLEMENTATION_GUIDE.md}
 * build it: validation, the normalised model, then registrable-domain extraction.
 *
 * <p>Registrable-domain extraction is where a Public Suffix List dependency (or a
 * bundled snapshot of the list) will land. That is an infrastructure concern, so it
 * belongs behind this package's interface rather than inside
 * {@code analysis.domain}, which must stay dependency-free.
 */
package com.lyanhkhoa.linksentry.analysis.normalization;
