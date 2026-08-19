package com.lyanhkhoa.linksentry.auth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lyanhkhoa.linksentry.auth.application.MailDeliveryException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Covers every documented failure category — missing configuration, SMTP
 * authentication, SMTP connectivity/timeout, and any other send failure —
 * collapsing to the same fixed {@link MailDeliveryException}, and that none of
 * them can leak the recipient, the code, or the provider's own diagnostic text
 * into the exception the rest of the app sees. The distinguishing detail lives
 * only in the server-side log line this class writes, never in the exception.
 */
class SmtpRegistrationCodeSenderTest {

    private static final String EMAIL = "person@example.com";
    private static final String CODE = "123456";
    private static final Duration TTL = Duration.ofMinutes(10);

    @Test
    @DisplayName("no JavaMailSender bean available is a safe unavailable error")
    void missingMailSenderIsSafeError() {
        ObjectProvider<JavaMailSender> provider = emptyProvider();
        SmtpRegistrationCodeSender sender = new SmtpRegistrationCodeSender(provider, "from@example.com");

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageNotContaining(EMAIL)
                .hasMessageNotContaining(CODE);
    }

    @Test
    @DisplayName("a blank configured from-address is a safe unavailable error, sender never invoked")
    void blankFromAddressIsSafeErrorAndNeverSends() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpRegistrationCodeSender sender = new SmtpRegistrationCodeSender(providerOf(mailSender), "  ");

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL)).isInstanceOf(MailDeliveryException.class);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("an SMTP authentication rejection is a safe unavailable error, never the provider's credential text")
    void authenticationFailureIsSafeError() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailAuthenticationException("535 authentication failed for from@example.com"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
        SmtpRegistrationCodeSender sender = new SmtpRegistrationCodeSender(providerOf(mailSender), "from@example.com");

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageNotContaining("535")
                .hasMessageNotContaining("from@example.com")
                .hasMessageNotContaining(EMAIL);
    }

    @Test
    @DisplayName("an SMTP connection timeout is a safe unavailable error, never the socket detail")
    void connectivityTimeoutIsSafeError() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException(
                        "Mail server connection failed", new SocketTimeoutException("connect timed out")))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
        SmtpRegistrationCodeSender sender = new SmtpRegistrationCodeSender(providerOf(mailSender), "from@example.com");

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageNotContaining("timed out")
                .hasMessageNotContaining(EMAIL);
    }

    @Test
    @DisplayName("an unreachable host is also bucketed as connectivity, and stays a safe unavailable error")
    void unreachableHostIsSafeError() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("Mail server connection failed", new ConnectException("Connection refused")))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
        SmtpRegistrationCodeSender sender = new SmtpRegistrationCodeSender(providerOf(mailSender), "from@example.com");

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL)).isInstanceOf(MailDeliveryException.class);
    }

    @Test
    @DisplayName("any other send failure is still a safe unavailable error, never the provider's response text")
    void otherSendFailureIsSafeError() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("552 mailbox full for person@example.com"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));
        SmtpRegistrationCodeSender sender = new SmtpRegistrationCodeSender(providerOf(mailSender), "from@example.com");

        assertThatThrownBy(() -> sender.send(EMAIL, CODE, TTL))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageNotContaining("552")
                .hasMessageNotContaining(EMAIL);
    }

    @Test
    @DisplayName("a successful send builds a message with only the recipient, code, and TTL, and throws nothing")
    void successfulSendBuildsExpectedMessage() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpRegistrationCodeSender sender = new SmtpRegistrationCodeSender(providerOf(mailSender), "from@example.com");

        sender.send(EMAIL, CODE, TTL);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("from@example.com");
        assertThat(message.getTo()).containsExactly(EMAIL);
        assertThat(message.getText()).contains(CODE).contains("10 minutes");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender mailSender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> emptyProvider() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
