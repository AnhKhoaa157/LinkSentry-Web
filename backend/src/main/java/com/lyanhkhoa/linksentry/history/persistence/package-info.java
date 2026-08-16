/**
 * JPA adapters for scan history.
 *
 * <p><strong>Empty by design</strong> (Exercise 10). Spring Data JPA, Flyway and the
 * PostgreSQL driver are already on the classpath, and
 * {@code src/main/resources/db/migration} is ready for the first migration.
 *
 * <p>Entities and repositories belong here and nowhere else. The dependency arrow
 * points inward: this package may reference domain types, and the domain must never
 * reference an entity.
 *
 * <p>Verify the SQL against real PostgreSQL through Testcontainers. H2 is adequate
 * for checking that an application context starts; it is not adequate for verifying
 * a dialect, a column type, or constraint behaviour you intend to ship.
 */
package com.lyanhkhoa.linksentry.history.persistence;
