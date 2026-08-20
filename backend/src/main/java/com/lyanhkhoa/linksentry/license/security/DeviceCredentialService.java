package com.lyanhkhoa.linksentry.license.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Creates high-entropy device credentials and public activation codes, and the only representation of a
 * credential ever persisted.
 *
 * <p>A credential is verified the same way {@code auth.security.TokenService} verified a bearer token:
 * hash the presented value and look it up by its unique hash column, never by comparing raw values. That
 * lookup-by-hash pattern is what makes the comparison constant-time-safe in practice, since no branch in
 * this codebase ever compares two raw secrets byte-by-byte.
 */
@Component
public class DeviceCredentialService {

    private static final int CREDENTIAL_BYTES = 32;
    private static final String ACTIVATION_CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int ACTIVATION_CODE_GROUPS = 2;
    private static final int ACTIVATION_CODE_GROUP_LENGTH = 4;

    private final SecureRandom secureRandom = new SecureRandom();

    /** Returns a URL-safe, unpadded, cryptographically random device credential. */
    public String newRawCredential() {
        byte[] bytes = new byte[CREDENTIAL_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Returns the fixed-length SHA-256 representation stored in the database. */
    public String sha256(String rawCredential) {
        if (rawCredential == null) {
            throw new IllegalArgumentException("Credential is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawCredential.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Returns a short public code such as {@code K7H9-QX3P}: safe to display, copy, and hand to an
     * administrator. Excludes visually ambiguous characters (0/O, 1/I/L) since it is meant to be read and
     * typed by a human.
     */
    public String newActivationCode() {
        StringBuilder builder = new StringBuilder();
        for (int group = 0; group < ACTIVATION_CODE_GROUPS; group++) {
            if (group > 0) {
                builder.append('-');
            }
            for (int i = 0; i < ACTIVATION_CODE_GROUP_LENGTH; i++) {
                builder.append(ACTIVATION_CODE_ALPHABET.charAt(secureRandom.nextInt(ACTIVATION_CODE_ALPHABET.length())));
            }
        }
        return builder.toString();
    }

    /** Trims and uppercases an admin-supplied activation code before lookup; the stored form is exact. */
    public static String normalizeActivationCode(String rawActivationCode) {
        return rawActivationCode == null ? "" : rawActivationCode.trim().toUpperCase(Locale.ROOT);
    }
}
