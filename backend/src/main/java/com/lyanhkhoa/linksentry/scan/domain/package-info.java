/**
 * Use-case-level types for the scan feature.
 *
 * <p><strong>Empty by design.</strong> Reserved for concepts that belong to the
 * <em>scan</em> as an event rather than to the analysis itself — a scan identifier,
 * the engine version, the record of when a scan happened.
 *
 * <p>Distinct from {@code analysis.domain}, which models the URL and its risk. The
 * split matters: analysis is a pure function of a string, while a scan is something
 * that occurred at a point in time and may be stored.
 */
package com.lyanhkhoa.linksentry.scan.domain;
