/**
 * Scan history domain model.
 *
 * <p>Exercise 10 adds the immutable safe snapshot used by the completed scan
 * flow. The domain type deliberately omits the raw input.
 *
 * <p>When it is time, the governing constraint is what may be stored: the
 * <em>redacted</em> representation only. Never the raw URL, never the query string.
 * Each row also carries the engine version that produced it, so old rows stay
 * interpretable after the rules change.
 */
package com.lyanhkhoa.linksentry.history.domain;
