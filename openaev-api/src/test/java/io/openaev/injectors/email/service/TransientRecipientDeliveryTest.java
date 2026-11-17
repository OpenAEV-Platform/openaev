package io.openaev.injectors.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Execution;
import io.openaev.database.model.User;
import io.openaev.execution.ExecutionContext;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the email send path delivers to a recipient that has NO database row — the transient
 * {@code new User()} the email-change flow hands to {@link io.openaev.service.MailingService} for
 * both the confirmation link (new address) and the completion notice (previous address).
 *
 * <p>This is a pure unit test on the real {@link EmailService}, the component that turns a
 * recipient's {@link ExecutionContext} into the actual {@link MimeMessage} and calls {@link
 * SmtpService#send}. It deliberately avoids the full Spring context: the send pipeline is exercised
 * for real, with only the final SMTP hop and the PGP/IMAP side-steps replaced by mocks, so the
 * assertion sees the message the real code produced. This slices out the Spring context that fails
 * to start in this environment; it does not exercise the MailingService -> injector hop (that is
 * where the recipient's {@link ExecutionContext} is built, and {@code MailingServiceTest} mocks
 * both the executor and ExecutionContextService, so that hop has no end-to-end coverage here). The
 * body/link content of the notices is asserted in {@code MeApiTest}.
 */
@DisplayName("Email delivery to a non-persisted recipient")
class TransientRecipientDeliveryTest {

  @Test
  @DisplayName("A non-persisted recipient's address reaches the SMTP message, link-free body")
  void deliversToNonPersistedRecipient() throws Exception {
    EmailPgp emailPgp = mock(EmailPgp.class);
    ImapService imapService = mock(ImapService.class);
    SmtpService smtpService = mock(SmtpService.class);
    EmailService emailService = new EmailService(emailPgp, imapService, smtpService);

    // A recipient built exactly like the email-change flow's transient recipients: a User that
    // carries only an id and an address and is never written to the database.
    String previousAddress = "previous-owner@elsewhere.invalid";
    String newAddress = "new-owner@elsewhere.invalid";
    User nonPersistedRecipient = new User();
    nonPersistedRecipient.setId(UUID.randomUUID().toString());
    nonPersistedRecipient.setEmail(previousAddress);

    String subject = "OpenAEV account email changed";
    String body =
        """
            Hi,<br/>
            <br/>
            Your OpenAEV account's email address was changed to %s.<br/>
            <br/>
            If you did not make this change, please take steps to secure your account.<br/>
            """
            .formatted(newAddress);

    when(smtpService.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

    emailService.sendEmail(
        new Execution(),
        List.of(new ExecutionContext(nonPersistedRecipient, null)),
        "noreply@openaev.io",
        null,
        List.of("noreply@openaev.io"),
        null,
        subject,
        body,
        Collections.emptyList());

    // If the non-persisted recipient had been dropped before delivery, send() would never be called
    // (or would carry no TO address) and these assertions would FAIL — which is the point.
    org.mockito.ArgumentCaptor<MimeMessage> sent =
        org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
    verify(smtpService).send(sent.capture());

    MimeMessage message = sent.getValue();
    InternetAddress[] recipients =
        (InternetAddress[]) message.getRecipients(Message.RecipientType.TO);
    assertThat(recipients).isNotNull();
    assertThat(recipients).extracting(InternetAddress::getAddress).containsExactly(previousAddress);

    // The message that actually went out names the new address and carries no confirmation link.
    MimeMultipart content = (MimeMultipart) message.getContent();
    String html = (String) content.getBodyPart(0).getContent();
    assertThat(html).contains(newAddress);
    assertThat(html).doesNotContain("/api/me/confirm-email-change/");
  }
}
