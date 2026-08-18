package com.lyanhkhoa.linksentry.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.lyanhkhoa.linksentry.auth.application.AuthService;
import com.lyanhkhoa.linksentry.auth.application.InvalidCredentialsException;
import com.lyanhkhoa.linksentry.auth.persistence.AuthSessionEntity;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataAuthSessionRepository;
import com.lyanhkhoa.linksentry.auth.persistence.SpringDataUserAccountRepository;
import com.lyanhkhoa.linksentry.auth.persistence.UserAccountEntity;
import com.lyanhkhoa.linksentry.auth.security.AuthenticatedUser;
import com.lyanhkhoa.linksentry.auth.security.TokenService;
import com.lyanhkhoa.linksentry.common.config.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("registration returns a one-time opaque token and persists only a password hash and token hash")
    void registrationDoesNotPersistRawSecrets() {
        SpringDataUserAccountRepository users = mock(SpringDataUserAccountRepository.class);
        SpringDataAuthSessionRepository sessions = mock(SpringDataAuthSessionRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        when(users.existsByEmail("person@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(UserAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.save(any(AuthSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthService service = new AuthService(
                users,
                sessions,
                encoder,
                new TokenService(),
                new AuthProperties(Duration.ofHours(1)),
                CLOCK);

        String rawPassword = "correct-horse-123";
        var response = service.register(new RegisterRequest(" Person@Example.com ", rawPassword));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.accessToken()).doesNotContain(rawPassword);
        assertThat(response.user().email()).isEqualTo("person@example.com");

        var userCaptor = org.mockito.ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(users).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(userCaptor.getValue().getPasswordHash()).startsWith("$2");

        var sessionCaptor = org.mockito.ArgumentCaptor.forClass(AuthSessionEntity.class);
        verify(sessions).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getTokenHash()).hasSize(64);
        assertThat(sessionCaptor.getValue().getTokenHash()).isNotEqualTo(response.accessToken());
    }

    @Test
    @DisplayName("login compares unknown and wrong-password attempts once with the same safe failure")
    void loginFailureUsesDummyComparison() {
        SpringDataUserAccountRepository users = mock(SpringDataUserAccountRepository.class);
        SpringDataAuthSessionRepository sessions = mock(SpringDataAuthSessionRepository.class);
        PasswordEncoder encoder = spy(new BCryptPasswordEncoder());
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        UserAccountEntity existingUser = new UserAccountEntity(
                UUID.randomUUID(),
                "person@example.com",
                encoder.encode("different-password"),
                NOW);
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(existingUser));
        AuthService service = new AuthService(
                users,
                sessions,
                encoder,
                new TokenService(),
                new AuthProperties(Duration.ofHours(1)),
                CLOCK);

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
        AuthService service = new AuthService(
                mock(SpringDataUserAccountRepository.class),
                sessions,
                new BCryptPasswordEncoder(),
                new TokenService(),
                new AuthProperties(Duration.ofHours(1)),
                CLOCK);
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
        AuthService service = new AuthService(
                mock(SpringDataUserAccountRepository.class),
                sessions,
                new BCryptPasswordEncoder(),
                new TokenService(),
                new AuthProperties(Duration.ofHours(1)),
                CLOCK);

        service.logout(new AuthenticatedUser(user.getUserId(), user.getEmail(), sessionId, session.getExpiresAt()));

        assertThat(session.getRevokedAt()).isEqualTo(NOW);
    }
}
