package io.openaev.injectors.email.service;

import io.openaev.config.OpenAEVConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformMailSender {

  @Value("${openaev.mail.imap.enabled}")
  private boolean imapEnabled;

  @Value("${openaev.mail.imap.username}")
  private String imapUsername;

  private final OpenAEVConfig openAEVConfig;

  public String getEmail() {
    return imapEnabled ? imapUsername : openAEVConfig.getDefaultMailer();
  }

  public String getName() {
    return openAEVConfig.getDefaultMailerName();
  }

  public String getReplyTo() {
    return imapEnabled ? imapUsername : openAEVConfig.getDefaultReplyTo();
  }
}
