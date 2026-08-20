package com.lyanhkhoa.linksentry.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Resend HTTPS API credentials for registration verification email, bound from
 * {@code linksentry.mail.resend.*}. Both fields may be blank at startup — a
 * deployment without them yet still boots, and {@code auth.provider.ResendRegistrationCodeSender}
 * turns a blank value into the same fixed {@code 503 EMAIL_DELIVERY_UNAVAILABLE}
 * response at send time, matching the SMTP adapter it replaces. See
 * {@code docs/SECURITY_BOUNDARY.md} §9.
 *
 * @param apiKey secret bearer credential for the Resend API; read only from environment, never logged
 * @param from   the configured "From" header, e.g. {@code LinkSentry <noreply@verified-domain.com>};
 *               the domain must be verified in Resend
 */
@Validated
@ConfigurationProperties(prefix = "linksentry.mail.resend")
public record ResendMailProperties(String apiKey, String from) {}
