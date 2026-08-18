package com.lyanhkhoa.linksentry.auth.application;

import com.lyanhkhoa.linksentry.auth.api.AuthResponse;
import com.lyanhkhoa.linksentry.auth.api.AuthUserResponse;
import com.lyanhkhoa.linksentry.auth.api.LoginRequest;
import com.lyanhkhoa.linksentry.auth.api.RegisterRequest;
import com.lyanhkhoa.linksentry.auth.api.SessionResponse;
import com.lyanhkhoa.linksentry.auth.persistence.AuthSessionEntity;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataAuthSessionRepository;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataUserAccountRepository;
import com.lyanhkhoa.linksentry.auth.persistence.UserAccountEntity;
import com.lyanhkhoa.linksentry.auth.security.AuthenticatedUser;
import com.lyanhkhoa.linksentry.auth.security.TokenService;
import com.lyanhkhoa.linksentry.common.config.AuthProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns account registration and opaque bearer-session lifecycle. */
@Service
public class AuthService {

    private static final String BEARER_TOKEN_TYPE = "Bearer";
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final SpringDataUserAccountRepository userRepository;
    private final SpringDataAuthSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthProperties authProperties;
    private final Clock clock;

    public AuthService(
            SpringDataUserAccountRepository userRepository,
            SpringDataAuthSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AuthProperties authProperties,
            Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        UserAccountEntity user = new UserAccountEntity(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                Instant.now(clock));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Keep concurrent duplicates in the same conflict response shape as the pre-check.
            throw new EmailAlreadyRegisteredException();
        }
        return issueSession(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserAccountEntity user = userRepository.findByEmail(email).orElse(null);
        String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return issueSession(user);
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            return Optional.empty();
        }
        return sessionRepository.findActiveByTokenHash(tokenService.sha256(rawToken), Instant.now(clock))
                .map(session -> new AuthenticatedUser(
                        session.getUser().getUserId(),
                        session.getUser().getEmail(),
                        session.getSessionId(),
                        session.getExpiresAt()));
    }

    @Transactional
    public void logout(AuthenticatedUser authenticatedUser) {
        sessionRepository.findById(authenticatedUser.sessionId())
                .ifPresent(session -> session.revoke(Instant.now(clock)));
    }

    public SessionResponse currentSession(AuthenticatedUser authenticatedUser) {
        return new SessionResponse(
                authenticatedUser.expiresAt(), new AuthUserResponse(authenticatedUser.email()));
    }

    private AuthResponse issueSession(UserAccountEntity user) {
        Instant createdAt = Instant.now(clock);
        Instant expiresAt = createdAt.plus(authProperties.sessionTtl());
        String rawToken = tokenService.newRawToken();
        AuthSessionEntity session = new AuthSessionEntity(
                UUID.randomUUID(), user, tokenService.sha256(rawToken), expiresAt, createdAt);
        sessionRepository.save(session);
        return new AuthResponse(
                rawToken, BEARER_TOKEN_TYPE, expiresAt, new AuthUserResponse(user.getEmail()));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
