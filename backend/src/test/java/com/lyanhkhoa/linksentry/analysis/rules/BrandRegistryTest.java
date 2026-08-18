package com.lyanhkhoa.linksentry.analysis.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrandRegistryTest {

    private static final Brand VIETCOMBANK =
            new Brand("vietcombank", "Vietcombank", List.of("vietcombank"), List.of("vietcombank.com.vn"));
    private static final Brand TECHCOMBANK =
            new Brand("techcombank", "Techcombank", List.of("techcombank"), List.of("techcombank.com.vn"));

    @Test
    @DisplayName("rejects an empty brand list")
    void rejectsEmptyRegistry() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BrandRegistry(List.of()));
    }

    @Test
    @DisplayName("rejects a duplicate brand id")
    void rejectsDuplicateId() {
        Brand duplicate = new Brand("vietcombank", "Other Bank", List.of("otherbank"), List.of("other-bank.example"));

        assertThatIllegalArgumentException().isThrownBy(() -> new BrandRegistry(List.of(VIETCOMBANK, duplicate)));
    }

    @Test
    @DisplayName("preserves configured order")
    void preservesConfiguredOrder() {
        BrandRegistry registry = new BrandRegistry(List.of(TECHCOMBANK, VIETCOMBANK));

        assertThat(registry.brands()).containsExactly(TECHCOMBANK, VIETCOMBANK);
    }
}
