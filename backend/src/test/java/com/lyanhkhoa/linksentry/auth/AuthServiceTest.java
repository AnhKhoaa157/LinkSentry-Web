package com.lyanhkhoa.linksentry.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lyanhkhoa.linksentry.auth.api.LoginRequest;
import com.lyanhkhoa.linksentry.auth.api.RegisterRequest;
import com.lyanhkhoa.linksentry.auth.api.RegistrationVerificationRequest;
import com.lyanhkhoa.linksentry.auth.application.AuthService;
import com.lyanhkhoa.linksentry.auth.application.InvalidCredentialsException;
import com.lyanhkhoa.linksentry.auth.application.RegistrationCodeSender;
import com.lyanhkhoa.linksentry.auth.persistence.AuthSessionEntity;
import com.lyanhkhoa.linksentry.auth.persistence.RegistrationVerificationEntity;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataAuthSessionRepository;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataRegistrationVerificationRepository;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataUserAccountRepository;
import com.lyanhkhoa.linksentry.auth.persistence.UserAccountEntity;
import com.lyanhkhoa.linksentry.auth.security.AuthenticatedUser;
import com.lyanhkhoa.linksentry.auth.security.TokenService;
import com.lyanhkhoa.linksentry.common.config.AuthOtpProperties;
import com.lyanhkhoa.linksentry.common.config.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthProperties AUTH_PROPERTIES = new AuthProperties(Duration.ofHours(1));
    private static final AuthOtpProperties OTP_PROPERTIES =
            new AuthOtpProperties(Duration.ofMinutes(10), 5, "no-reply@example.com");

    @Test
    @DisplayName("registration stores only hashes, sends a code, and creates the account after verification")
    void registrationVerifiesEmailBeforeCreatingAccount() {
        SpringDataUserAccountRepository users = mock(SpringDataUserAccountRepository.class);
        SpringDataAuthSessionRepository sessions = mock(SpringDataAuthSessionRepository.class);
        SpringDataRegistrationVerificationRepository registrations =
                mock(SpringDataRegistrationVerificationRepository.class);
        RegistrationCodeSender sender = mock(RegistrationCodeSender.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        when(users.existsByEmail("person@example.com")).thenReturn(false);
        when(registrations.findById("person@example.com")).thenReturn(Optional.empty());
        when(registrations.saveAndFlush(any(RegistrationVerificationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthService service = new AuthService(
                users,
                sessions,
                registrations,
                encoder,
                new TokenService(),
                sender,
                AUTH_PROPERTIES,
                OTP_PROPERTIES,
                CLOCK);

        String rawPassword = "correct-horse-123";
        var started = service.register(new RegisterRequest(" Person@Example.com ", rawPassword));

        assertThat(started.message()).contains("verification code");
        ArgumentCaptor<RegistrationVerificationEntity> pendingCaptor =
                ArgumentCaptor.forClass(RegistrationVerificationEntity.class);
        verify(registrations).saveAndFlush(pendingCaptor.capture());
        RegistrationVerificationEntity pending = pendingCaptor.getValue();
        assertThat(pending.getPasswordHash()).startsWith("$2").doesNotContain(rawPassword);
        assertThat(pending.getCodeHash()).startsWith("$2");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("person@example.com"), codeCaptor.capture(), eq(Duration.ofMinutes(10)));
        String code = codeCaptor.getValue();
        assertThat(code).matches("\\d{6}");
        assertThat(encoder.matches(code, pending.getCodeHash())).isTrue();

        when(registrations.findById("person@example.com")).thenReturn(Optional.of(pending));
        when(users.saveAndFlush(any(UserAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.save(any(AuthSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.verifyRegistration(new RegistrationVerificationRequest("person@example.com", code));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("person@example.com");
        verify(registrations).delete(pending);
    }

    @Test
    @DisplayName("login compares unknown and wrong-password attempts once with the same safe failure")
    void loginFailureUsesDummyComparison() {
        SpringDataUserAccountRepository users = mock(SpringDataUserAccountRepository.class);
        SpringDataAuthSessionRepository sessions = mock(SpringDataAuthSessionRepository.class);
        PasswordEncoder encoder = spy(new BCryptPasswordEncoder());
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        UserAccountEntity existingUser = new UserAccountEntity(
                UUID.randomUUID(), "person@example.com", encoder.encode("different-password"), NOW);
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(existingUser));
        AuthService service = service(users, sessions, encoder);

        Throwable unknownEmailFailure = catchThrowable(
                () -> service.login(new LoginRequest("unknown@example.com", "secret-password")));
        Throwable wrongPasswordFailure = catchThrowable(
                () -> service.login(new LoginRequest("person@example.com", "secret-password")));

        assertThat(unknownEmailFailure)
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect.");
        assertThat(wrongPasswordFailure)
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect.");

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(encoder, times(2)).matches(eq("secret-password"), passwordHash.capture());
        assertThat(passwordHash.getAllValues()).hasSize(2);
        assertThat(passwordHash.getAllValues().get(0)).startsWith("$2a$10$").hasSize(60);
        assertThat(passwordHash.getAllValues().get(1)).isEqualTo(existingUser.getPasswordHash());
        assertThat(passwordHash.getAllValues().get(0)).isNotEqualTo(passwordHash.getAllValues().get(1));
    }

    @Test
    @DisplayName("authentication delegates expiry and revocation decisions to the active-session query")
    void inactiveTokensAreRejectedWithoutReturningIdentity() {
        SpringDataAuthSessionRepository sessions = mock(SpringDataAuthSessionRepository.class);
        AuthService service = service(mock(SpringDataUserAccountRepository.class), sessions,
                new BCryptPasswordEncoder());
        when(sessions.findActiveByTokenHash(any(String.class), any(Instant.class))).thenReturn(Optional.empty());

        assertThat(service.authenticate("test-only-token")).isEmpty();
        verify(sessions).findActiveByTokenHash(any(String.class), any(Instant.class));
    }

    @Test
    @DisplayName("logout marks the server-side session revoked without needing the bearer value")
    void logoutRevokesSession() {
        SpringDataAuthSessionRepository sessions = mock(SpringDataAuthSessionRepository.class);
        UserAccountEntity user = new UserAccountEntity(
                UUID.randomUUID(), "person@example.com", "hash", NOW);
        UUID sessionId = UUID.randomUUID();
        AuthSessionEntity session = new AuthSessionEntity(
                sessionId, user, "a".repeat(64), NOW.plus(Duration.ofHours(1)), NOW);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        AuthService service = service(mock(SpringDataUserAccountRepository.class), sessions,
                new BCryptPasswordEncoder());

        service.logout(new AuthenticatedUser(user.getUserId(), user.getEmail(), sessionId, session.getExpiresAt()));

        assertThat(session.getRevokedAt()).isEqualTo(NOW);
    }

    private static AuthService service(
            SpringDataUserAccountRepository users,
            SpringDataAuthSessionRepository sessions,
            PasswordEncoder encoder) {
        return new AuthService(
                users,
                sessions,
                mock(SpringDataRegistrationVerificationRepository.class),
                encoder,
                new TokenService(),
                mock(RegistrationCodeSender.class),
                AUTH_PROPERTIES,
                OTP_PROPERTIES,
                CLOCK);
    }
}
