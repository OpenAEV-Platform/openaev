package io.openaev.injectors.email.service;

import io.openaev.database.repository.SettingRepository;
import io.openaev.utils.base.ExternalServiceBase;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SmtpService extends ExternalServiceBase {

  private static final String SMTP_SETTINGS_KEY = "smtp_service_available";

  private final JavaMailSender mailSender;
  private final SettingRepository settingRepository;

  @PostConstruct
  private void init() {
    this.testConnection();
  }

  public MimeMessage createMimeMessage() {
    return this.mailSender.createMimeMessage();
  }

  public void send(MimeMessage mimeMessage) {
    this.mailSender.send(mimeMessage);
  }

  // Check connection every 10 seconds
  @Scheduled(fixedDelay = 10000, initialDelay = 10000)
  public void connectionListener() {
    this.testConnection();
  }

  private void testConnection() {
    try {
      if (mailSender instanceof JavaMailSenderImpl javaMailSender) {
        javaMailSender.testConnection();
        this.saveServiceState(SMTP_SETTINGS_KEY, true);
      } else {
        this.saveServiceState(SMTP_SETTINGS_KEY, false);
      }
    } catch (Exception e) {
      log.warn(e.getMessage());
      this.saveServiceState(SMTP_SETTINGS_KEY, false);
    }
  }

  @Override
  public SettingRepository getSettingRepository() {
    return settingRepository;
  }
}
