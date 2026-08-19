package com.lyanhkhoa.linksentry.auth.provider;

import com.lyanhkhoa.linksentry.auth.application.MailDeliveryException;
import com.lyanhkhoa.linksentry.auth.application.RegistrationCodeSender;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends only the short-lived registration code; submitted URLs never enter this adapter. */
@Component
public class SmtpRegistrationCodeSender implements RegistrationCodeSender {

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
        } catch (MailException exception) {
            // The provider message can contain the recipient or SMTP response; never expose or log it here.
            throw new MailDeliveryException();
        }
    }
}
