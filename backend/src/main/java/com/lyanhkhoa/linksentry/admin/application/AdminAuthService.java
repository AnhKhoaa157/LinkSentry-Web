package com.lyanhkhoa.linksentry.admin.application;

import com.lyanhkhoa.linksentry.admin.api.AdminAuthResponse;
import com.lyanhkhoa.linksentry.admin.api.AdminIdentityResponse;
import com.lyanhkhoa.linksentry.admin.api.AdminLoginRequest;
import com.lyanhkhoa.linksentry.admin.api.AdminSessionResponse;
import com.lyanhkhoa.linksentry.admin.config.AdminAuthProperties;
import com.lyanhkhoa.linksentry.admin.domain.AdminIdentity;
import com.lyanhkhoa.linksentry.admin.persistence.AdminSessionEntity;
import com.lyanhkhoa.linksentry.admin.persistence.AdminUserEntity;
import com.lyanhkhoa.linksentry.admin.persistence.SpringDataAdminSessionRepository;
import com.lyanhkhoa.linksentry.admin.persistence.SpringDataAdminUserRepository;
import com.lyanhkhoa.linksentry.admin.security.AdminTokenService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns administrator bootstrap and opaque bearer-session lifecycle. */
@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private static final String BEARER_TOKEN_TYPE = "Bearer";
    // A real BCrypt hash of an unrelated fixed value, so a lookup miss still pays the same
    // comparison cost as a real one — no timing signal for "does this username exist."
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final int MIN_BOOTSTRAP_PASSWORD_LENGTH = 8;

    private final SpringDataAdminUserRepository adminUserRepository;
    private final SpringDataAdminSessionRepository adminSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminTokenService tokenService;
    private final AdminAuthProperties properties;
    private final Clock clock;

    public AdminAuthService(
            SpringDataAdminUserRepository adminUserRepository,
            SpringDataAdminSessionRepository adminSessionRepository,
            PasswordEncoder passwordEncoder,
            AdminTokenService tokenService,
            AdminAuthProperties properties,
            Clock clock) {
        this.adminUserRepository = adminUserRepository;
        this.adminSessionRepository = adminSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Creates exactly one administrator account from the configured bootstrap username/password,
     * but only while none exists yet. Never overwrites or resets an existing account, and never
     * fails startup: a blank, missing, or too-short bootstrap password is logged as a safe warning
     * and skipped rather than thrown.
     */
    @Transactional
    public void bootstrapIfNeeded() {
        if (adminUserRepository.count() > 0) {
            return;
        }

        String username = trimToNull(properties.bootstrapUsername());
        String password = properties.bootstrapPassword();
        if (username == null || password == null || password.length() < MIN_BOOTSTRAP_PASSWORD_LENGTH) {
            log.warn(
                    "Admin bootstrap skipped: ADMIN_BOOTSTRAP_USERNAME/ADMIN_BOOTSTRAP_PASSWORD is missing, "
                            + "blank, or shorter than {} characters. No administrator account exists yet.",
                    MIN_BOOTSTRAP_PASSWORD_LENGTH);
            return;
        }

        AdminUserEntity admin =
                new AdminUserEntity(UUID.randomUUID(), username, passwordEncoder.encode(password), Instant.now(clock));
        try {
            adminUserRepository.saveAndFlush(admin);
            log.info("Bootstrapped the initial administrator account.");
        } catch (DataIntegrityViolationException exception) {
            // Another concurrent startup already created the first admin; nothing to do.
            log.info("Admin bootstrap skipped: an administrator account already exists.");
        }
    }

    @Transactional
    public AdminAuthResponse login(AdminLoginRequest request) {
        String username = request.username().trim();
        AdminUserEntity admin = adminUserRepository.findByUsername(username).orElse(null);
        String passwordHash = admin == null ? DUMMY_PASSWORD_HASH : admin.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (admin == null || !passwordMatches) {
            throw new InvalidAdminCredentialsException();
        }
        return issueSession(admin);
    }

    @Transactional(readOnly = true)
    public Optional<AdminIdentity> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            return Optional.empty();
        }
        return adminSessionRepository
                .findActiveByTokenHash(tokenService.sha256(rawToken), Instant.now(clock))
                .map(session -> new AdminIdentity(
                        session.getAdminUser().getAdminUserId(),
                        session.getAdminUser().getUsername(),
                        session.getSessionId(),
                        session.getExpiresAt()));
    }

    @Transactional
    public void logout(AdminIdentity adminIdentity) {
        adminSessionRepository.findById(adminIdentity.sessionId()).ifPresent(session -> session.revoke(Instant.now(clock)));
    }

    public AdminSessionResponse currentSession(AdminIdentity adminIdentity) {
        return new AdminSessionResponse(adminIdentity.expiresAt(), new AdminIdentityResponse(adminIdentity.username()));
    }

    private AdminAuthResponse issueSession(AdminUserEntity admin) {
        Instant createdAt = Instant.now(clock);
        Instant expiresAt = createdAt.plus(properties.sessionTtl());
        String rawToken = tokenService.newRawToken();
        AdminSessionEntity session =
                new AdminSessionEntity(UUID.randomUUID(), admin, tokenService.sha256(rawToken), expiresAt, createdAt);
        adminSessionRepository.save(session);
        return new AdminAuthResponse(rawToken, BEARER_TOKEN_TYPE, expiresAt, new AdminIdentityResponse(admin.getUsername()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
