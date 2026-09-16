package io.openaev.rest.scenario.configuration;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Test double for the SMTP sender: builds real {@link MimeMessage} instances and discards the
 * actual send.
 *
 * <p>{@code createMimeMessage()} must be stubbed explicitly. It is abstract on the {@link
 * JavaMailSender} interface, so {@code CALLS_REAL_METHODS} alone returned {@code null} and every
 * email path NPEd - silently, because the email injector funnels failures into execution traces
 * instead of propagating them. Tests were passing over a delivery that never happened.
 */
@Profile("test")
@Configuration
public class WithMockEmailSender {
  @Bean
  @Primary
  public JavaMailSender mailSender() {
    JavaMailSender mailSender = Mockito.mock(JavaMailSender.class, Mockito.CALLS_REAL_METHODS);
    // A fresh message per call: callers mutate it (recipients, body, attachments).
    Mockito.doAnswer(invocation -> new MimeMessage((Session) null))
        .when(mailSender)
        .createMimeMessage();
    return mailSender;
  }
}
