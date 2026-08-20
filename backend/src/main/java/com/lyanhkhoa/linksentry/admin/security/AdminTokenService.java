package com.lyanhkhoa.linksentry.admin.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Creates opaque admin bearer values and the only representation persisted for them. */
@Component
public class AdminTokenService {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Returns a URL-safe, unpadded, cryptographically random bearer value. */
    public String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Returns the fixed-length SHA-256 representation stored in the database. */
    public String sha256(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Token is required");
        }
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
