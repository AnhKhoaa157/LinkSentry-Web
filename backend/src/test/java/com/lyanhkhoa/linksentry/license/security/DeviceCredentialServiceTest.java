package com.lyanhkhoa.linksentry.license.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceCredentialServiceTest {

    private static final Pattern ACTIVATION_CODE_SHAPE = Pattern.compile("^[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}$");

    private final DeviceCredentialService service = new DeviceCredentialService();

    @Test
    @DisplayName("newRawCredential returns a high-entropy, URL-safe value that is never blank or predictable")
    void newRawCredentialIsHighEntropy() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String credential = service.newRawCredential();
            assertThat(credential).isNotBlank();
            assertThat(credential).matches("^[A-Za-z0-9_-]+$");
            assertThat(generated.add(credential)).isTrue();
        }
    }

    @Test
    @DisplayName("sha256 is deterministic for the same input and differs for different input")
    void sha256IsDeterministicAndDistinct() {
        String credential = service.newRawCredential();

        assertThat(service.sha256(credential)).isEqualTo(service.sha256(credential));
        assertThat(service.sha256(credential)).isNotEqualTo(service.sha256(service.newRawCredential()));
        assertThat(service.sha256(credential)).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("sha256 rejects a null credential rather than hashing a placeholder")
    void sha256RejectsNull() {
        assertThatThrownBy(() -> service.sha256(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("newActivationCode is human-shareable, excludes ambiguous characters, and is effectively unique")
    void newActivationCodeIsSafeAndUnique() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = service.newActivationCode();
            assertThat(code).matches(ACTIVATION_CODE_SHAPE);
            assertThat(code).doesNotContainAnyWhitespaces();
            assertThat(generated.add(code)).isTrue();
        }
    }

    @Test
    @DisplayName("normalizeActivationCode trims and uppercases; a null input normalizes to an empty string")
    void normalizeActivationCodeTrimsAndUppercases() {
        assertThat(DeviceCredentialService.normalizeActivationCode("  k7h9-qx3p  ")).isEqualTo("K7H9-QX3P");
        assertThat(DeviceCredentialService.normalizeActivationCode(null)).isEmpty();
    }
}
