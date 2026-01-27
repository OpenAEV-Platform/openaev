package io.openaev.service;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.injectors.email.service.SmtpService;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import java.util.Properties;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
public class SmtpServiceTest {

  private JavaMailSenderImpl mailSender;

  @Autowired private SettingRepository settingRepository;

  @Autowired private SmtpService smtpService;

  @BeforeEach
  void resetMocks() {
    this.mailSender = Mockito.mock(JavaMailSenderImpl.class, Mockito.CALLS_REAL_METHODS);
    ReflectionTestUtils.setField(smtpService, "mailSender", mailSender);
    ReflectionTestUtils.setField(smtpService, "enabled", true);
  }

  @Test
  void createMimeMessageTest() {
    Properties props = new Properties();
    Session session = Session.getInstance(props);
    MimeMessage message = new MimeMessage(session);

    doReturn(message).when(mailSender).createMimeMessage();

    MimeMessage mimeMessage = smtpService.createMimeMessage();

    assertEquals(message, mimeMessage);
  }

  @Test
  void sendMessageTest() {
    Properties props = new Properties();
    Session session = Session.getInstance(props);
    MimeMessage message = new MimeMessage(session);

    doNothing().when(mailSender).send(message);

    smtpService.send(message);

    verify(mailSender).send(message);
  }

  @Test
  void testConnectionSuccess() throws MessagingException {
    doNothing().when(mailSender).testConnection();

    smtpService.connectionListener();

    Optional<Setting> actualSmtpAvailable = settingRepository.findByKey("smtp_service_available");

    assertThat(actualSmtpAvailable)
        .isPresent()
        .get()
        .satisfies(setting -> assertThat(setting.getValue()).isEqualTo("true"));
  }

  @Test
  void testConnectionFail() throws MessagingException {
    doThrow(MessagingException.class).when(mailSender).testConnection();

    smtpService.connectionListener();

    Optional<Setting> actualSmtpAvailable = settingRepository.findByKey("smtp_service_available");

    AssertionsForClassTypes.assertThat(actualSmtpAvailable)
        .isPresent()
        .get()
        .satisfies(
            setting -> AssertionsForClassTypes.assertThat(setting.getValue()).isEqualTo("false"));
  }
}
