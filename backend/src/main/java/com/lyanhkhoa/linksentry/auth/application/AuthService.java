package com.lyanhkhoa.linksentry.auth.application;

import com.lyanhkhoa.linksentry.auth.api.AuthResponse;
import com.lyanhkhoa.linksentry.auth.api.AuthUserResponse;
import com.lyanhkhoa.linksentry.auth.api.LoginRequest;
import com.lyanhkhoa.linksentry.auth.api.RegisterRequest;
import com.lyanhkhoa.linksentry.auth.api.RegistrationResendRequest;
import com.lyanhkhoa.linksentry.auth.api.RegistrationStartedResponse;
import com.lyanhkhoa.linksentry.auth.api.RegistrationVerificationRequest;
import com.lyanhkhoa.linksentry.auth.api.SessionResponse;
import com.lyanhkhoa.linksentry.auth.persistence.AuthSessionEntity;
import com.lyanhkhoa.linksentry.auth.persistence.RegistrationVerificationEntity;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataAuthSessionRepository;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataRegistrationVerificationRepository;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataUserAccountRepository;
import com.lyanhkhoa.linksentry.auth.persistence.UserAccountEntity;
import com.lyanhkhoa.linksentry.auth.security.AuthenticatedUser;
import com.lyanhkhoa.linksentry.auth.security.TokenService;
import com.lyanhkhoa.linksentry.common.config.AuthProperties;
import com.lyanhkhoa.linksentry.common.config.AuthOtpProperties;
import java.time.Clock;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns email-verified account registration and opaque bearer-session lifecycle. */
@Service
public class AuthService {

    private static final String BEARER_TOKEN_TYPE = "Bearer";
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String DUMMY_CODE_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final int VERIFICATION_CODE_BOUND = 900_000;
    private static final int VERIFICATION_CODE_OFFSET = 100_000;

    private final SpringDataUserAccountRepository userRepository;
    private final SpringDataAuthSessionRepository sessionRepository;
    private final SpringDataRegistrationVerificationRepository registrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RegistrationCodeSender registrationCodeSender;
    private final AuthProperties authProperties;
    private final AuthOtpProperties otpProperties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public AuthService(
            SpringDataUserAccountRepository userRepository,
            SpringDataAuthSessionRepository sessionRepository,
            SpringDataRegistrationVerificationRepository registrationRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            RegistrationCodeSender registrationCodeSender,
            AuthProperties authProperties,
            AuthOtpProperties otpProperties,
            Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.registrationRepository = registrationRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.registrationCodeSender = registrationCodeSender;
        this.authProperties = authProperties;
        this.otpProperties = otpProperties;
        this.clock = clock;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public AuthResponse registerLegacy(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        UserAccountEntity user = new UserAccountEntity(
                UUID.randomUUID(), email, passwordEncoder.encode(request.password()), Instant.now(clock));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Keep concurrent duplicates in the same conflict response shape as the pre-check.
            throw new EmailAlreadyRegisteredException();
        }
        return issueSession(user);
    }

    @Transactional
    public RegistrationStartedResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        Instant createdAt = Instant.now(clock);
        registrationRepository.deleteByExpiresAtBefore(createdAt);
        Instant expiresAt = createdAt.plus(otpProperties.ttl());
        String code = newVerificationCode();
        RegistrationVerificationEntity pending = registrationRepository.findById(email).orElse(null);
        if (pending == null) {
            pending = new RegistrationVerificationEntity(
                    email,
                    passwordEncoder.encode(request.password()),
                    passwordEncoder.encode(code),
                    expiresAt,
                    createdAt);
        } else {
            pending.replace(
                    passwordEncoder.encode(request.password()), passwordEncoder.encode(code), expiresAt, createdAt);
        }
        registrationRepository.saveAndFlush(pending);
        registrationCodeSender.send(email, code, otpProperties.ttl());
        return new RegistrationStartedResponse(
                "A verification code was sent to your email address.", expiresAt);
    }

    @Transactional
    public RegistrationStartedResponse resendRegistrationCode(RegistrationResendRequest request) {
        String email = normalizeEmail(request.email());
        RegistrationVerificationEntity pending = registrationRepository.findById(email)
                .orElseThrow(InvalidVerificationCodeException::new);
        Instant createdAt = Instant.now(clock);
        Instant expiresAt = createdAt.plus(otpProperties.ttl());
        String code = newVerificationCode();
        pending.replace(pending.getPasswordHash(), passwordEncoder.encode(code), expiresAt, createdAt);
        registrationRepository.saveAndFlush(pending);
        registrationCodeSender.send(email, code, otpProperties.ttl());
        return new RegistrationStartedResponse(
                "A verification code was sent to your email address.", expiresAt);
    }

    @Transactional
    public AuthResponse verifyRegistration(RegistrationVerificationRequest request) {
        String email = normalizeEmail(request.email());
        RegistrationVerificationEntity pending = registrationRepository.findById(email).orElse(null);
        String codeHash = pending == null ? DUMMY_CODE_HASH : pending.getCodeHash();
        boolean codeMatches = passwordEncoder.matches(request.code(), codeHash);
        if (pending == null
                || !codeMatches
                || pending.getAttempts() >= otpProperties.maxAttempts()
                || !pending.getExpiresAt().isAfter(Instant.now(clock))) {
            if (pending != null && pending.getAttempts() < otpProperties.maxAttempts()) {
                pending.recordFailedAttempt();
                registrationRepository.save(pending);
            }
            throw new InvalidVerificationCodeException();
        }

        UserAccountEntity user = new UserAccountEntity(
                UUID.randomUUID(), email, pending.getPasswordHash(), Instant.now(clock));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Keep concurrent duplicates in the same conflict response shape as the pre-check.
            throw new EmailAlreadyRegisteredException();
        }
        registrationRepository.delete(pending);
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

    private String newVerificationCode() {
        return Integer.toString(VERIFICATION_CODE_OFFSET + secureRandom.nextInt(VERIFICATION_CODE_BOUND));
    }
}
