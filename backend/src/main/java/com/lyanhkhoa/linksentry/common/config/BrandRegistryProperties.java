package com.lyanhkhoa.linksentry.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Curated brand data for {@code BRAND_DOMAIN_MISMATCH}, bound from
 * {@code linksentry.brands.entries}.
 *
 * <p>This is hand-maintained data, not runtime discovery: each entry names a brand
 * this deployment has deliberately chosen to protect, its recognisable hostname
 * tokens, and the registrable domains it actually controls. Semantic validation
 * (lowercase-ASCII tokens/domains, no duplicates) happens when
 * {@code AnalysisConfig} builds the framework-free
 * {@link com.lyanhkhoa.linksentry.analysis.rules.BrandRegistry} from this bean, so
 * a malformed entry fails application startup rather than producing a silently
 * wrong finding.
 *
 * @param entries configured brands, in the deterministic order used to break ties
 *                when a hostname's tokens match more than one brand
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.brands")
public record BrandRegistryProperties(@NotEmpty List<@Valid Entry> entries) {

    /**
     * @param id              stable identifier for this brand entry
     * @param displayName     human-readable name, safe to render in a finding
     * @param tokens          lowercase ASCII hostname-label tokens that identify this brand
     * @param officialDomains lowercase ASCII registrable domains this brand controls
     */
    public record Entry(
            @NotBlank String id,
            @NotBlank String displayName,
            @NotEmpty List<@NotBlank String> tokens,
            @NotEmpty List<@NotBlank String> officialDomains) {}
}
