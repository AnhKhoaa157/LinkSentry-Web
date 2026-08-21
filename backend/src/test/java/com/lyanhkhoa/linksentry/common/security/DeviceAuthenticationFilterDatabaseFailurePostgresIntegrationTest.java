package com.lyanhkhoa.linksentry.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * M6.1's final correction: proves against the <strong>real</strong> Spring Security filter chain,
 * a real {@code MockMvc}, and a real (but deliberately failing) datasource — not a mocked slice —
 * that a database failure during {@link DeviceAuthenticationFilter}'s credential resolution
 * ({@code DeviceService.authenticate}, which runs before {@code AnonymousTrialFilter}'s quota
 * check is ever reached) fails closed as the fixed {@code 503 TRIAL_QUOTA_UNAVAILABLE} on {@code
 * POST /api/v1/scans} — never a bare {@code 500}, and never a fallback trial admission.
 *
 * <p>The application starts against a real PostgreSQL Testcontainer (Flyway and Hibernate
 * validation both succeed normally), then a {@link ToggleableDataSource} wrapping the real
 * connection pool is flipped to fail <em>after</em> startup, for this test only — simulating a
 * database outage that begins mid-lifetime, not one that would have prevented the application from
 * starting in the first place.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceAuthenticationFilterDatabaseFailurePostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("linksentry_device_auth_failure_test")
            .withUsername("linksentry")
            .withPassword("integration-test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    // A @Bean returning a DataSource here would trip Spring Boot's own
    // DataSourceAutoConfiguration @ConditionalOnMissingBean(DataSource.class) guard, silently
    // skipping creation of the real Hikari pool Flyway and JPA both need. Building the real pool
    // by hand from the container's own connection details sidesteps that entirely — no dependency
    // on the framework's default "dataSource" bean at all.
    @TestConfiguration
    static class ToggleableDataSourceConfig {
        @Bean
        @Primary
        ToggleableDataSource toggleableDataSource() {
            HikariDataSource real = new HikariDataSource();
            real.setJdbcUrl(POSTGRES.getJdbcUrl());
            real.setUsername(POSTGRES.getUsername());
            real.setPassword(POSTGRES.getPassword());
            real.setDriverClassName("org.postgresql.Driver");
            return new ToggleableDataSource(real);
        }
    }

    /** Delegates every call to the real pool unless {@link #setFailing} was told otherwise. */
    static final class ToggleableDataSource extends DelegatingDataSource {
        private final AtomicBoolean failing = new AtomicBoolean(false);

        ToggleableDataSource(DataSource delegate) {
            super(delegate);
        }

        void setFailing(boolean value) {
            failing.set(value);
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (failing.get()) {
                throw new SQLException("simulated database outage");
            }
            return super.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            if (failing.get()) {
                throw new SQLException("simulated database outage");
            }
            return super.getConnection(username, password);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ToggleableDataSource toggleableDataSource;

    @AfterEach
    void restoreDataSource() {
        toggleableDataSource.setFailing(false);
    }

    @Test
    @DisplayName(
            "a database failure during device-credential resolution on POST /api/v1/scans fails closed as 503 TRIAL_QUOTA_UNAVAILABLE — never 500, never admission, nothing sensitive leaked")
    void databaseFailureDuringCredentialResolutionFailsClosed() throws Exception {
        toggleableDataSource.setFailing(true);

        var result = mockMvc.perform(post("/api/v1/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Device some-presented-credential")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account?token=super-secret-session-token\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TRIAL_QUOTA_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("The trial scan quota is temporarily unavailable. Please try again shortly."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain(
                        "example.com",
                        "super-secret-session-token",
                        "some-presented-credential",
                        "SQLException",
                        "simulated database outage")
                .doesNotContainIgnoringCase("stack");
        assertThat(result.getResponse().getHeaderNames())
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("retry"));
        assertThat(result.getResponse().getHeader("Retry-After")).isNull();
    }

    @Test
    @DisplayName("once the database recovers, the same route works normally again (no permanently stuck failure state)")
    void requestSucceedsAgainOnceDatabaseRecovers() throws Exception {
        toggleableDataSource.setFailing(true);
        mockMvc.perform(post("/api/v1/scans")
                        .header(HttpHeaders.AUTHORIZATION, "Device some-presented-credential")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account\"}"))
                .andExpect(status().isServiceUnavailable());

        toggleableDataSource.setFailing(false);

        // No credential this time: falls through to the ordinary 401 TRIAL_DEVICE_REQUIRED path,
        // proving the request pipeline recovered rather than staying wedged in a failure mode.
        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/account\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TRIAL_DEVICE_REQUIRED"));
    }
}
