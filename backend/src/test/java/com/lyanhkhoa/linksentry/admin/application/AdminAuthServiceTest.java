package com.lyanhkhoa.linksentry.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lyanhkhoa.linksentry.admin.api.AdminAuthResponse;
import com.lyanhkhoa.linksentry.admin.api.AdminLoginRequest;
import com.lyanhkhoa.linksentry.admin.config.AdminAuthProperties;
import com.lyanhkhoa.linksentry.admin.domain.AdminIdentity;
import com.lyanhkhoa.linksentry.admin.persistence.AdminSessionEntity;
import com.lyanhkhoa.linksentry.admin.persistence.AdminUserEntity;
import com.lyanhkhoa.linksentry.admin.persistence.SpringDataAdminSessionRepository;
import com.lyanhkhoa.linksentry.admin.persistence.SpringDataAdminUserRepository;
import com.lyanhkhoa.linksentry.admin.security.AdminTokenService;
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

class AdminAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AdminAuthProperties BLANK_BOOTSTRAP_PROPERTIES =
            new AdminAuthProperties("", "", Duration.ofMinutes(30));

    @Test
    @DisplayName("bootstrap creates exactly one admin, storing only its BCrypt hash, when none exists yet")
    void bootstrapCreatesOneAdminFromConfiguredCredentials() {
        SpringDataAdminUserRepository users = mock(SpringDataAdminUserRepository.class);
        when(users.count()).thenReturn(0L);
        when(users.saveAndFlush(any(AdminUserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AdminAuthProperties properties = new AdminAuthProperties("ops", "correct-horse-123", Duration.ofMinutes(30));
        AdminAuthService service = service(users, mock(SpringDataAdminSessionRepository.class), encoder, properties);

        service.bootstrapIfNeeded();

        ArgumentCaptor<AdminUserEntity> captor = ArgumentCaptor.forClass(AdminUserEntity.class);
        verify(users).saveAndFlush(captor.capture());
        AdminUserEntity created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo("ops");
        assertThat(created.getPasswordHash()).startsWith("$2").doesNotContain("correct-horse-123");
        assertThat(encoder.matches("correct-horse-123", created.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("bootstrap never overwrites or duplicates an existing admin account")
    void bootstrapIsSkippedWhenAnAdminAlreadyExists() {
        SpringDataAdminUserRepository users = mock(SpringDataAdminUserRepository.class);
        when(users.count()).thenReturn(1L);
        AdminAuthService service = service(
                users,
                mock(SpringDataAdminSessionRepository.class),
                new BCryptPasswordEncoder(),
                new AdminAuthProperties("ops", "correct-horse-123", Duration.ofMinutes(30)));

        service.bootstrapIfNeeded();

        verify(users, never()).saveAndFlush(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("bootstrap is safely skipped, not failed, when the bootstrap credentials are blank or too short")
    void bootstrapSkipsBlankOrWeakConfiguration() {
        SpringDataAdminUserRepository users = mock(SpringDataAdminUserRepository.class);
        when(users.count()).thenReturn(0L);
        AdminAuthService blankService = service(
                users, mock(SpringDataAdminSessionRepository.class), new BCryptPasswordEncoder(), BLANK_BOOTSTRAP_PROPERTIES);
        AdminAuthService shortPasswordService = service(
                users,
                mock(SpringDataAdminSessionRepository.class),
                new BCryptPasswordEncoder(),
                new AdminAuthProperties("ops", "short", Duration.ofMinutes(30)));

        blankService.bootstrapIfNeeded();
        shortPasswordService.bootstrapIfNeeded();

        verify(users, never()).saveAndFlush(any(AdminUserEntity.class));
    }

    @Test
    @DisplayName("login compares unknown-username and wrong-password attempts with the same safe failure")
    void loginFailureUsesDummyComparison() {
        SpringDataAdminUserRepository users = mock(SpringDataAdminUserRepository.class);
        PasswordEncoder encoder = spy(new BCryptPasswordEncoder());
        when(users.findByUsername("unknown")).thenReturn(Optional.empty());
        AdminUserEntity existingAdmin =
                new AdminUserEntity(UUID.randomUUID(), "ops", encoder.encode("different-password"), NOW);
        when(users.findByUsername("ops")).thenReturn(Optional.of(existingAdmin));
        AdminAuthService service =
                service(users, mock(SpringDataAdminSessionRepository.class), encoder, BLANK_BOOTSTRAP_PROPERTIES);

        Throwable unknownUsernameFailure =
                catchThrowable(() -> service.login(new AdminLoginRequest("unknown", "secret-password")));
        Throwable wrongPasswordFailure =
                catchThrowable(() -> service.login(new AdminLoginRequest("ops", "secret-password")));

        assertThat(unknownUsernameFailure)
                .isInstanceOf(InvalidAdminCredentialsException.class)
                .hasMessage("Username or password is incorrect.");
        assertThat(wrongPasswordFailure)
                .isInstanceOf(InvalidAdminCredentialsException.class)
                .hasMessage("Username or password is incorrect.");

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(encoder, times(2)).matches(eq("secret-password"), passwordHash.capture());
        assertThat(passwordHash.getAllValues().get(0)).isNotEqualTo(passwordHash.getAllValues().get(1));
    }

    @Test
    @DisplayName("successful login issues a session whose token hash, not the raw token, is persisted")
    void loginIssuesHashedSession() {
        SpringDataAdminUserRepository users = mock(SpringDataAdminUserRepository.class);
        SpringDataAdminSessionRepository sessions = mock(SpringDataAdminSessionRepository.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AdminUserEntity admin = new AdminUserEntity(UUID.randomUUID(), "ops", encoder.encode("correct-horse-123"), NOW);
        when(users.findByUsername("ops")).thenReturn(Optional.of(admin));
        when(sessions.save(any(AdminSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AdminAuthService service = service(users, sessions, encoder, BLANK_BOOTSTRAP_PROPERTIES);

        AdminAuthResponse response = service.login(new AdminLoginRequest("ops", "correct-horse-123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(response.admin().username()).isEqualTo("ops");

        ArgumentCaptor<AdminSessionEntity> sessionCaptor = ArgumentCaptor.forClass(AdminSessionEntity.class);
        verify(sessions).save(sessionCaptor.capture());
        AdminSessionEntity persisted = sessionCaptor.getValue();
        assertThat(persisted.getTokenHash()).hasSize(64).isNotEqualTo(response.accessToken());
        assertThat(persisted.getTokenHash()).isEqualTo(new AdminTokenService().sha256(response.accessToken()));
    }

    @Test
    @DisplayName("authentication delegates expiry and revocation decisions to the active-session query")
    void inactiveTokensAreRejectedWithoutReturningIdentity() {
        SpringDataAdminSessionRepository sessions = mock(SpringDataAdminSessionRepository.class);
        AdminAuthService service = service(
                mock(SpringDataAdminUserRepository.class), sessions, new BCryptPasswordEncoder(), BLANK_BOOTSTRAP_PROPERTIES);
        when(sessions.findActiveByTokenHash(any(String.class), any(Instant.class))).thenReturn(Optional.empty());

        assertThat(service.authenticate("test-only-token")).isEmpty();
        verify(sessions).findActiveByTokenHash(any(String.class), any(Instant.class));
    }

    @Test
    @DisplayName("logout marks the server-side session revoked without needing the bearer value")
    void logoutRevokesSession() {
        SpringDataAdminSessionRepository sessions = mock(SpringDataAdminSessionRepository.class);
        AdminUserEntity admin = new AdminUserEntity(UUID.randomUUID(), "ops", "hash", NOW);
        UUID sessionId = UUID.randomUUID();
        AdminSessionEntity session =
                new AdminSessionEntity(sessionId, admin, "a".repeat(64), NOW.plus(Duration.ofMinutes(30)), NOW);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        AdminAuthService service = service(
                mock(SpringDataAdminUserRepository.class), sessions, new BCryptPasswordEncoder(), BLANK_BOOTSTRAP_PROPERTIES);

        service.logout(new AdminIdentity(admin.getAdminUserId(), admin.getUsername(), sessionId, session.getExpiresAt()));

        assertThat(session.getRevokedAt()).isEqualTo(NOW);
    }

    private static AdminAuthService service(
            SpringDataAdminUserRepository users,
            SpringDataAdminSessionRepository sessions,
            PasswordEncoder encoder,
            AdminAuthProperties properties) {
        return new AdminAuthService(users, sessions, encoder, new AdminTokenService(), properties, CLOCK);
    }
}
