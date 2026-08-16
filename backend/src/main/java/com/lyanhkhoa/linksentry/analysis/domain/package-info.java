/**
 * The analysis domain: contracts and immutable value objects.
 *
 * <p><strong>This package must stay framework-free.</strong> No Spring, no Jakarta
 * Persistence, no Jackson, no HTTP client, nothing database-related. Plain Java
 * only.
 *
 * <p>That constraint is not purity for its own sake. The analyzer is the part of
 * LinkSentry worth testing exhaustively, and a framework-free domain means its
 * tests are plain JUnit with no application context to start — so a table-driven
 * suite of hundreds of URLs stays fast enough to run on every save.
 *
 * <p>Current state: contracts only. {@link
 * com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer} and {@link
 * com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule} have no implementations —
 * see {@code docs/MANUAL_IMPLEMENTATION_GUIDE.md}.
 */
package com.lyanhkhoa.linksentry.analysis.domain;
