package com.lyanhkhoa.linksentry.auth.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards the margin between the configured SMTP timeouts and the frontend's 10s
 * Axios timeout (see the frontend HTTP client). Each stage previously defaulted
 * to 10000ms — matching the frontend timeout exactly — so a single slow SMTP
 * connect/write/read could let the browser's own clock expire first, and the
 * caller saw a raw network failure instead of this backend's intended
 * {@code 503 EMAIL_DELIVERY_UNAVAILABLE} JSON body. The worst case (every stage
 * timing out in the same send attempt) must stay comfortably under 10000ms so
 * the JSON response always has time to leave the backend first.
 */
@SpringBootTest
@ActiveProfiles("test")
class SmtpTimeoutConfigurationTest {

    private static final long FRONTEND_TIMEOUT_MILLIS = 10_000;

    @Autowired
    private JavaMailSender javaMailSender;

    @Test
    @DisplayName("configured SMTP connect/read/write timeouts leave real margin under the frontend's 10s timeout")
    void smtpTimeoutsLeaveMarginUnderFrontendTimeout() {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) javaMailSender;
        long connectTimeout = Long.parseLong(sender.getJavaMailProperties().getProperty("mail.smtp.connectiontimeout"));
        long readTimeout = Long.parseLong(sender.getJavaMailProperties().getProperty("mail.smtp.timeout"));
        long writeTimeout = Long.parseLong(sender.getJavaMailProperties().getProperty("mail.smtp.writetimeout"));

        long worstCaseTotal = connectTimeout + readTimeout + writeTimeout;

        assertThat(worstCaseTotal)
                .as("sum of connect+read+write timeouts, the worst case for one send attempt")
                .isLessThan(FRONTEND_TIMEOUT_MILLIS);
        assertThat(connectTimeout).isLessThan(FRONTEND_TIMEOUT_MILLIS);
        assertThat(readTimeout).isLessThan(FRONTEND_TIMEOUT_MILLIS);
        assertThat(writeTimeout).isLessThan(FRONTEND_TIMEOUT_MILLIS);
    }
}
