package io.openaev.injectors.email.service;

import io.openaev.config.OpenAEVConfig;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the sender identity (address, display name, reply-to) used for platform emails.
 *
 * <p>When IMAP is enabled, the IMAP username is used as sender and reply-to so that replies land
 * in the mailbox polled by the platform. Some mail servers use a plain login (e.g. {@code
 * openaev}) rather than an email address, though; such a value is not a valid address and SMTP
 * servers refuse the message. In that case we fall back to the configured default mailer /
 * reply-to.
 */
@Component
@RequiredArgsConstructor
public class PlatformMailSender {

  @Value("${openaev.mail.imap.enabled}")
  private boolean imapEnabled;

  @Value("${openaev.mail.imap.username}")
  private String imapUsername;

  private final OpenAEVConfig openAEVConfig;

  public String getEmail() {
    return useImapUsernameAsAddress() ? imapUsername.trim() : openAEVConfig.getDefaultMailer();
  }

  public String getName() {
    return openAEVConfig.getDefaultMailerName();
  }

  public String getReplyTo() {
    return useImapUsernameAsAddress() ? imapUsername.trim() : openAEVConfig.getDefaultReplyTo();
  }

  private boolean useImapUsernameAsAddress() {
    return imapEnabled
        && imapUsername != null
        && EmailValidator.getInstance().isValid(imapUsername.trim());
  }
}
