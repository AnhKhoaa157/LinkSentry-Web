package com.lyanhkhoa.linksentry.analysis.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable, ordered collection of curated {@link Brand} entries.
 *
 * <p>Order is significant: when a hostname's tokens match more than one configured
 * brand, {@link BrandDomainMismatchRule} picks exactly one — the first brand, in
 * this list's order, whose token matched — so the rule's outcome never depends on
 * iteration order of a {@link java.util.Map} or on which brand "sounds" more
 * relevant.
 *
 * <p>Validation here is the startup check: a malformed or duplicate entry fails
 * fast when this registry (and, in production, the Spring bean that builds it from
 * {@code linksentry.brands.*}) is constructed, rather than surfacing later as a
 * silently wrong finding.
 */
public final class BrandRegistry {

    private final List<Brand> brands;

    public BrandRegistry(List<Brand> brands) {
        Objects.requireNonNull(brands, "brands");
        if (brands.isEmpty()) {
            throw new IllegalArgumentException("brands must not be empty");
        }

        Set<String> seenIds = new HashSet<>();
        for (Brand brand : brands) {
            Objects.requireNonNull(brand, "brand");
            if (!seenIds.add(brand.id())) {
                throw new IllegalArgumentException("duplicate brand id '" + brand.id() + "'");
            }
        }

        this.brands = List.copyOf(brands);
    }

    /** Configured brands, in curated order. */
    public List<Brand> brands() {
        return brands;
    }
}
