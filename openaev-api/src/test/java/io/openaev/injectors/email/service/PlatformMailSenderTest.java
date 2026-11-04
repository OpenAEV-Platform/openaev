package io.openaev.injectors.email.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.config.OpenAEVConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("Platform mail sender tests")
class PlatformMailSenderTest {

  private static final String DEFAULT_MAILER = "no-reply@openaev.io";
  private static final String DEFAULT_REPLY_TO = "contact@openaev.io";

  private OpenAEVConfig openAEVConfig;
  private PlatformMailSender platformMailSender;

  @BeforeEach
  void setUp() {
    openAEVConfig = mock(OpenAEVConfig.class);
    when(openAEVConfig.getDefaultMailer()).thenReturn(DEFAULT_MAILER);
    when(openAEVConfig.getDefaultReplyTo()).thenReturn(DEFAULT_REPLY_TO);
    platformMailSender = new PlatformMailSender(openAEVConfig);
  }

  private void configureImap(boolean enabled, String username) {
    ReflectionTestUtils.setField(platformMailSender, "imapEnabled", enabled);
    ReflectionTestUtils.setField(platformMailSender, "imapUsername", username);
  }

  @Test
  @DisplayName("IMAP disabled: uses default mailer and reply-to")
  void imapDisabled_usesDefaults() {
    configureImap(false, "imap@mail.com");

    assertEquals(DEFAULT_MAILER, platformMailSender.getEmail());
    assertEquals(DEFAULT_REPLY_TO, platformMailSender.getReplyTo());
  }

  @Test
  @DisplayName("IMAP enabled with an email username: uses the IMAP username")
  void imapEnabledWithEmailUsername_usesImapUsername() {
    configureImap(true, "imap@mail.com");

    assertEquals("imap@mail.com", platformMailSender.getEmail());
    assertEquals("imap@mail.com", platformMailSender.getReplyTo());
  }

  @Test
  @DisplayName("IMAP enabled with a plain login: falls back to defaults")
  void imapEnabledWithPlainLogin_fallsBackToDefaults() {
    configureImap(true, "openaev");

    assertEquals(DEFAULT_MAILER, platformMailSender.getEmail());
    assertEquals(DEFAULT_REPLY_TO, platformMailSender.getReplyTo());
  }

  @Test
  @DisplayName("IMAP enabled with a blank or missing username: falls back to defaults")
  void imapEnabledWithBlankUsername_fallsBackToDefaults() {
    configureImap(true, "  ");
    assertEquals(DEFAULT_MAILER, platformMailSender.getEmail());
    assertEquals(DEFAULT_REPLY_TO, platformMailSender.getReplyTo());

    configureImap(true, null);
    assertEquals(DEFAULT_MAILER, platformMailSender.getEmail());
    assertEquals(DEFAULT_REPLY_TO, platformMailSender.getReplyTo());
  }
}
