package com.lyanhkhoa.linksentry.auth.provider;

import com.lyanhkhoa.linksentry.auth.application.MailDeliveryException;
import com.lyanhkhoa.linksentry.auth.application.RegistrationCodeSender;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends only the short-lived registration code; submitted URLs never enter this adapter. */
@Component
public class SmtpRegistrationCodeSender implements RegistrationCodeSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpRegistrationCodeSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public SmtpRegistrationCodeSender(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${linksentry.auth.otp.mail-from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String email, String code, Duration ttl) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || from.isBlank()) {
            log.warn("Registration email not sent [category=NOT_CONFIGURED]");
            throw new MailDeliveryException();
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Verify your LinkSentry account");
        message.setText("Your LinkSentry verification code is " + code + ". It expires in "
                + ttl.toMinutes() + " minutes. If you did not request this, you can ignore this email.");
        try {
            sender.send(message);
        } catch (MailAuthenticationException exception) {
            // Never exception.getMessage(): an SMTP auth rejection can quote the rejected
            // credential or a provider diagnostic string. Only the exception's own class
            // name is safe to log — the same discipline explanation.AnthropicExplanationProvider
            // already uses for a provider failure.
            log.warn(
                    "Registration email delivery failed [category=AUTHENTICATION, type={}]",
                    exception.getClass().getSimpleName());
            throw new MailDeliveryException();
        } catch (MailException exception) {
            // Same discipline: bucket by the exception type chain alone, never by message,
            // recipient, or the provider's own text.
            log.warn(
                    "Registration email delivery failed [category={}, type={}]",
                    classify(exception),
                    exception.getClass().getSimpleName());
            throw new MailDeliveryException();
        }
    }

    /** Distinguishes a connection/timeout failure from any other send failure by type alone. */
    private static String classify(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof UnknownHostException) {
                return "CONNECTIVITY";
            }
        }
        return "OTHER";
    }
}
