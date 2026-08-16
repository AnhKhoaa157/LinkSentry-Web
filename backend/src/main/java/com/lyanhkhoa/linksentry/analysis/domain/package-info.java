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
 * <p>The analyzer and rule contracts have framework-free implementations in this
 * module; Spring wiring remains outside the domain package.
 */
package com.lyanhkhoa.linksentry.analysis.domain;
