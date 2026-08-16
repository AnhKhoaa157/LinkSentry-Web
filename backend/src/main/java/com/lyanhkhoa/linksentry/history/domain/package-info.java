/**
 * Scan history domain model.
 *
 * <p><strong>Empty by design, and the last milestone.</strong> Exercise 10 — and
 * not before the stateless scan flow works end to end. Designing this table while
 * the analyzer's output is still moving guarantees a rewrite.
 *
 * <p>When it is time, the governing constraint is what may be stored: the
 * <em>redacted</em> representation only. Never the raw URL, never the query string.
 * Each row also carries the engine version that produced it, so old rows stay
 * interpretable after the rules change.
 */
package com.lyanhkhoa.linksentry.history.domain;
