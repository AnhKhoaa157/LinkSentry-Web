/**
 * Scan history application services.
 *
 * <p>This layer owns the retention policy and retrieval cutoff. The policy is
 * applied before a result is returned, while the scheduled cleanup removes old
 * rows from PostgreSQL.
 */
package com.lyanhkhoa.linksentry.history.application;
