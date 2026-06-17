package io.openaev.injectors.email.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Communication;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CommunicationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.FileService;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
class ImapServiceTenantScopeTest {

  @Mock private UserRepository userRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private CommunicationRepository communicationRepository;
  @Mock private FileService fileService;
  @Mock private Environment env;
  @Mock private SettingRepository settingRepository;

  @InjectMocks private ImapService imapService;

  @BeforeEach
  void beforeEach() {
    ReflectionTestUtils.setField(imapService, "username", "mailbox@openaev.test");
  }

  @Nested
  @DisplayName("Tenant-scoped parsing")
  class TenantScopedParsing {

    @Test
    @DisplayName("given_messageAlreadyAvailableInInjectTenant_should_notCreateCommunication")
    void given_messageAlreadyAvailableInInjectTenant_should_notCreateCommunication()
        throws Exception {
      // Arrange
      String tenantId = "tenant-a";
      String injectId = "inject-a";
      String messageId = "message-a";

      Inject inject = new Inject();
      inject.setId(injectId);
      inject.setTenant(new Tenant(tenantId));

      when(injectRepository.findById(injectId)).thenReturn(java.util.Optional.of(inject));
      when(communicationRepository.existsByIdentifierAndInjectTenantId(messageId, tenantId))
          .thenReturn(true);

      Message[] messages =
          new Message[] {
            buildMessage(messageId, "[inject_id=" + injectId + "]")
          };

      // Act
      ReflectionTestUtils.invokeMethod(imapService, "parseMessages", messages, false);

      // Assert
      verify(communicationRepository).existsByIdentifierAndInjectTenantId(messageId, tenantId);
      verify(userRepository, never())
          .findAllByEmailInIgnoreCaseAndTenantId(anyList(), anyString());
      verify(communicationRepository, never()).save(any(Communication.class));
    }
  }

  private MimeMessage buildMessage(String messageId, String body) throws Exception {
    String participantEmail = "participant@acme.test";
    Session session = Session.getInstance(new Properties());
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(participantEmail));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(participantEmail));
    message.setText(body);

    MimeMessage spy = org.mockito.Mockito.spy(message);
    doReturn(messageId).when(spy).getMessageID();
    return spy;
  }
}
