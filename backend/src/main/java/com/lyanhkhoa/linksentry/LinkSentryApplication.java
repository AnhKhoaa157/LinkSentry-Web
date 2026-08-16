package com.lyanhkhoa.linksentry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Entry point for the LinkSentry API.
 *
 * <p>LinkSentry analyses submitted URLs as text and explains why they look
 * suspicious. It never contacts a submitted URL; see
 * {@code docs/SECURITY_BOUNDARY.md} for the full set of prohibited behaviours.
 *
 * <p>{@code UserDetailsServiceAutoConfiguration} is excluded because the API has no
 * accounts. Left enabled, Spring Boot creates an in-memory user and logs a
 * generated password at startup — a credential nothing authenticates against, which
 * is worse than no credential at all: it invites someone to try using it. Excluding
 * the class by type rather than by property name means a future rename breaks the
 * compile instead of silently restoring the behaviour.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class LinkSentryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkSentryApplication.class, args);
    }
}
