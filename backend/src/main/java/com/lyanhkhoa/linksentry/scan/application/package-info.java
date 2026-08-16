/**
 * Application service layer of the scan feature.
 *
 * <p>The service sits between the controller and
 * {@link com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer}.
 *
 * <p>This layer owns what the domain deliberately leaves out: generating the scan
 * id, stamping the timestamp, attaching the engine version, and deciding whether
 * a result gets persisted. Keeping those here is what lets
 * {@code UrlAnalyzer.analyze} stay a pure function.
 */
package com.lyanhkhoa.linksentry.scan.application;
