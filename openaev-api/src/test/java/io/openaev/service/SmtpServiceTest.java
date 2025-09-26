package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.injectors.email.service.SmtpService;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SmtpServiceTest {

  @Mock private JavaMailSenderImpl mailSender;

  @Autowired private SettingRepository settingRepository;

  private SmtpService smtpService;

  @BeforeEach
  void setUp() throws NoSuchFieldException, IllegalAccessException {
    smtpService = new SmtpService(settingRepository);

    Field mailSenderField = SmtpService.class.getDeclaredField("mailSender");
    mailSenderField.setAccessible(true);
    mailSenderField.set(smtpService, mailSender);
  }

  @Test
  void createMimeMessageTest() {
    Properties props = new Properties();
    Session session = Session.getInstance(props);
    MimeMessage message = new MimeMessage(session);

    when(mailSender.createMimeMessage()).thenReturn(message);

    MimeMessage mimeMessage = smtpService.createMimeMessage();

    assertEquals(message, mimeMessage);
  }

  @Test
  void sendMessageTest() {
    Properties props = new Properties();
    Session session = Session.getInstance(props);
    MimeMessage message = new MimeMessage(session);

    smtpService.send(message);

    verify(mailSender).send(message);
  }

  @Test
  void testConnectionSuccess() throws MessagingException {
    doNothing().when(mailSender).testConnection();

    smtpService.connectionListener();

    Optional<Setting> setting = settingRepository.findByKey("smtp_service_available");
    assertFalse(setting.isEmpty());
    assertEquals("true", setting.get().getValue());
    assertTrue(smtpService.isServiceAvailable());
  }

  @Test
  void testConnectionFail() throws MessagingException {
    doThrow(MessagingException.class).when(mailSender).testConnection();

    smtpService.connectionListener();

    Optional<Setting> setting = settingRepository.findByKey("smtp_service_available");
    assertFalse(setting.isEmpty());
    assertEquals("false", setting.get().getValue());
    assertFalse(smtpService.isServiceAvailable());
  }
}
