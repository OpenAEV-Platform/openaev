package io.openaev.injectors.email.service;

import static io.openaev.database.model.ExecutionTrace.getNewInfoTrace;
import static io.openaev.database.model.ExecutionTrace.getNewSuccessTrace;
import static io.openaev.database.model.ExecutionTrace.getNewWarningTrace;
import static io.openaev.helper.TemplateHelper.buildContextualContent;
import static java.util.stream.Collectors.joining;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.DataAttachment;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.execution.ExecutionContext;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailService {

  @Value("${openaev.mail.imap.enabled}")
  private boolean imapEnabled;

  private final EmailPgp emailPgp;
  private final ImapService imapService;
  private final SmtpService smtpService;

  public void sendEmail(
      Execution execution,
      List<ExecutionContext> usersContext,
      String from,
      String fromName,
      List<String> replyTos,
      String inReplyTo,
      String subject,
      String message,
      List<DataAttachment> attachments)
      throws Exception {
    sendEmail(
        execution,
        usersContext,
        from,
        fromName,
        replyTos,
        inReplyTo,
        false,
        subject,
        message,
        attachments);
  }

  public void sendEmail(
      Execution execution,
      List<ExecutionContext> usersContext,
      String from,
      String fromName,
      List<String> replyTos,
      String inReplyTo,
      boolean mustBeEncrypted,
      String subject,
      String message,
      List<DataAttachment> attachments)
      throws Exception {
    sendEmailInternal(
        execution,
        usersContext,
        from,
        fromName,
        replyTos,
        inReplyTo,
        mustBeEncrypted,
        true,
        subject,
        message,
        attachments);
  }

  /**
   * Sends an already-rendered email, bypassing FreeMarker interpolation of the subject and body.
   * Used by the phishing injector, whose lure content is operator-authored (and deliberately
   * attacker-shaped) and must never be evaluated as a template - the per-recipient tracking link is
   * substituted upstream via a literal placeholder. Skipping interpolation removes the server-side
   * template-injection sink for this untrusted content.
   */
  public void sendPreRenderedEmail(
      Execution execution,
      List<ExecutionContext> usersContext,
      String from,
      String fromName,
      List<String> replyTos,
      String inReplyTo,
      String subject,
      String message,
      List<DataAttachment> attachments)
      throws Exception {
    sendEmailInternal(
        execution,
        usersContext,
        from,
        fromName,
        replyTos,
        inReplyTo,
        false,
        false,
        subject,
        message,
        attachments);
  }

  private void sendEmailInternal(
      Execution execution,
      List<ExecutionContext> usersContext,
      String from,
      String fromName,
      List<String> replyTos,
      String inReplyTo,
      boolean mustBeEncrypted,
      boolean interpolate,
      String subject,
      String message,
      List<DataAttachment> attachments)
      throws Exception {
    String contextualSubject = subject;
    String contextualBody = message;
    if (interpolate) {
      ExecutionContext interpolationContext = (ExecutionContext) usersContext.getFirst().clone();
      if (usersContext.size() > 1) {
        interpolationContext.remove("user");
      }
      contextualSubject = buildContextualContent(subject, interpolationContext);
      contextualBody = buildContextualContent(message, interpolationContext);
    }

    MimeMessage mimeMessage =
        buildMimeMessage(
            from, fromName, replyTos, inReplyTo, contextualSubject, contextualBody, attachments);
    mimeMessage.setRecipients(
        Message.RecipientType.TO,
        usersContext.stream()
            .map(
                uc -> {
                  try {
                    return new InternetAddress(uc.getUser().email());
                  } catch (AddressException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(InternetAddress[]::new));

    // request encryption but this is possible only for an email to a single recipient
    if (mustBeEncrypted && usersContext.size() == 1) {
      ExecutionContext singleUserContext = usersContext.getFirst();
      MimeMessage encMessage =
          getEncryptedMimeMessage(
              singleUserContext,
              from,
              fromName,
              replyTos,
              subject,
              singleUserContext.getUser().email(),
              mimeMessage);
      this.sendEmailWithRetry(execution, encMessage);
    } else {
      this.sendEmailWithRetry(execution, mimeMessage);
    }
    List<String> userIds = usersContext.stream().map(c -> c.getUser().id()).toList();
    execution.addTrace(
        getNewSuccessTrace(
            "Mail sent to "
                + usersContext.stream().map(c -> c.getUser().email()).collect(joining(", ")),
            ExecutionTraceAction.EXECUTION,
            userIds));
    // Store message in Imap after sending
    storeMessageImap(execution, mimeMessage, userIds);
  }

  private InternetAddress getInternetAddress(String email) {
    try {
      return new InternetAddress(email);
    } catch (AddressException e) {
      throw new IllegalArgumentException("Invalid email address: " + email, e);
    }
  }

  private void storeMessageImap(Execution execution, MimeMessage mimeMessage, List<String> userIds)
      throws InterruptedException {
    if (!imapEnabled) {
      execution.addTrace(
          getNewSuccessTrace(
              "Mail successfully send (imap disabled)", ExecutionTraceAction.COMPLETE, userIds));
      return;
    }
    if (execution.isRuntime()) {
      for (int i = 0; i < 3; i++) {
        try {
          imapService.storeSentMessage(mimeMessage);
          execution.addTrace(
              getNewSuccessTrace(
                  "Mail successfully stored in IMAP", ExecutionTraceAction.COMPLETE, userIds));
          return;
        } catch (Exception e) {
          execution.addTrace(
              getNewInfoTrace(
                  "Fail to store mail in IMAP " + e.getMessage(),
                  ExecutionTraceAction.EXECUTION,
                  userIds));
          Thread.sleep(2000);
        }
      }
      // The mail was already sent successfully; storing a copy in the IMAP sent folder is a
      // best-effort side step (it commonly fails when IMAP is not connected/configured). A failure
      // here must NOT fail the inject, so it is a WARNING (counted as success by the status
      // aggregation) rather than an ERROR that would flip the whole inject to "Error".
      execution.addTrace(
          getNewWarningTrace(
              "Mail sent, but failed to store a copy in the IMAP sent folder after 3 attempts",
              ExecutionTraceAction.COMPLETE,
              userIds));
    }
  }

  private MimeMessage buildMimeMessage(
      String from,
      String fromName,
      List<String> replyTos,
      String inReplyTo,
      String subject,
      String body,
      List<DataAttachment> attachments)
      throws Exception {
    MimeMessage mimeMessage = this.smtpService.createMimeMessage();
    setFromAddress(mimeMessage, from, fromName);
    mimeMessage.setReplyTo(
        replyTos.stream().map(this::getInternetAddress).toArray(InternetAddress[]::new));

    if (inReplyTo != null) {
      mimeMessage.setHeader("In-Reply-To", inReplyTo);
      mimeMessage.setHeader("References", inReplyTo);
    }
    mimeMessage.setSubject(subject, "utf-8");
    Multipart mailMultipart = new MimeMultipart("mixed");
    // Add mail content
    MimeBodyPart bodyPart = new MimeBodyPart();
    bodyPart.setContent(body, "text/html;charset=utf-8");
    mailMultipart.addBodyPart(bodyPart);
    // Add Attachments
    for (DataAttachment attachment : attachments) {
      MimeBodyPart aBodyPart = new MimeBodyPart();
      aBodyPart.setFileName(attachment.name());
      aBodyPart.setHeader("Content-Type", attachment.contentType());
      ByteArrayDataSource bds =
          new ByteArrayDataSource(attachment.data(), attachment.contentType());
      aBodyPart.setDataHandler(new DataHandler(bds));
      mailMultipart.addBodyPart(aBodyPart);
    }
    mimeMessage.setContent(mailMultipart);
    return mimeMessage;
  }

  private MimeMessage getEncryptedMimeMessage(
      ExecutionContext userContext,
      String from,
      String fromName,
      List<String> replyTos,
      String subject,
      String email,
      MimeMessage mimeMessage)
      throws IOException, MessagingException {
    PGPPublicKey userPgpKey = emailPgp.getUserPgpKey(userContext.getUser());
    // Need to create another email that will wrap everything.
    MimeMessage encMessage = this.smtpService.createMimeMessage();
    setFromAddress(encMessage, from, fromName);
    encMessage.setReplyTo(
        replyTos.stream().map(this::getInternetAddress).toArray(InternetAddress[]::new));
    encMessage.setSubject(subject, "utf-8");
    encMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(email));

    Multipart encMultipart = new MimeMultipart("encrypted; protocol=\"application/pgp-encrypted\"");
    // This is an OpenPGP/MIME encrypted message (RFC 4880 and 3156)
    InternetHeaders headers = new InternetHeaders();
    headers.addHeader("Content-Type", "application/pgp-encrypted");

    MimeBodyPart mimeExPart = new MimeBodyPart(headers, "Version: 1".getBytes());
    mimeExPart.setDescription("PGP/MIME version identification");
    encMultipart.addBodyPart(mimeExPart);

    // Export and crypt to basic email
    ByteArrayOutputStream multiEncStream = new ByteArrayOutputStream();
    mimeMessage.writeTo(multiEncStream);

    String encryptedEmail = emailPgp.encrypt(userPgpKey, multiEncStream.toString());

    MimeBodyPart encBodyPart = new MimeBodyPart();
    encBodyPart.setDisposition("inline");
    encBodyPart.setFileName("openpgp-encrypted-message.asc");
    encBodyPart.setContent(encryptedEmail, "application/octet-stream");
    encMultipart.addBodyPart(encBodyPart);
    // Fill the message with the multipart content
    encMessage.setContent(encMultipart);
    return encMessage;
  }

  private void setFromAddress(MimeMessage mimeMessage, String from, String fromName)
      throws MessagingException {
    if (hasText(fromName)) {
      try {
        mimeMessage.setFrom(new InternetAddress(from, fromName, "UTF-8"));
      } catch (java.io.UnsupportedEncodingException e) {
        mimeMessage.setFrom(from);
      }
    } else {
      mimeMessage.setFrom(from);
    }
  }

  private void sendEmailWithRetry(Execution execution, MimeMessage mimeMessage)
      throws InterruptedException {
    for (int i = 0; i < 3; i++) {
      try {
        this.smtpService.send(mimeMessage);
        return;
      } catch (Exception e) {
        execution.addTrace(
            getNewInfoTrace(
                "Failed to send mail" + e.getMessage(), ExecutionTraceAction.EXECUTION));
        Thread.sleep(2000);
      }
    }
    throw new InterruptedException("Failed to send mail after 3 attempts");
  }
}
