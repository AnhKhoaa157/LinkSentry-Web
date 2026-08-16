/**
 * JPA adapters for scan history.
 *
 * <p>Exercise 10 provides the Flyway schema, JPA entities, and repository adapter
 * for the safe history snapshot.
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
