package com.lyanhkhoa.linksentry.admin.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs {@link AdminAuthService#bootstrapIfNeeded()} once on every application startup.
 *
 * <p>Skipped when {@code spring.flyway.enabled} is {@code false} — never true in a real
 * deployment (see {@code application.yml}), but exactly the {@code test} profile's default for
 * fast context-loading tests, which deliberately run against a schema-less H2 instance (see {@code
 * application-test.yml} and {@code docs/ARCHITECTURE.md} §6). Querying {@code admin_user} before
 * any migration has run would otherwise fail every such test, not just this feature's own.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminAuthService adminAuthService;
    private final boolean flywayEnabled;

    public AdminBootstrapRunner(
            AdminAuthService adminAuthService, @Value("${spring.flyway.enabled:true}") boolean flywayEnabled) {
        this.adminAuthService = adminAuthService;
        this.flywayEnabled = flywayEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (flywayEnabled) {
            adminAuthService.bootstrapIfNeeded();
        }
    }
}
