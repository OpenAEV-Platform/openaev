package io.openaev.injectors.email.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Communication;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CommunicationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.injectors.email.model.EmailContent;
import io.openaev.service.FileService;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ImapServiceTenantScopeTest {

  @Mock private UserRepository userRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private CommunicationRepository communicationRepository;
  @Mock private FileService fileService;
  @Mock private Environment env;
  @Mock private SettingRepository settingRepository;

  private ImapService imapService;

  @BeforeEach
  void beforeEach() {
    imapService =
        new ImapService(
            userRepository,
            injectRepository,
            communicationRepository,
            fileService,
            env,
            settingRepository);
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

      Message[] messages = new Message[] {buildMessage(messageId, "[inject_id=" + injectId + "]")};

      // Act
      ReflectionTestUtils.invokeMethod(imapService, "parseMessages", messages, false);

      // Assert
      verify(communicationRepository).existsByIdentifierAndInjectTenantId(messageId, tenantId);
      verify(userRepository, never()).findAllByEmailInIgnoreCaseAndTenantId(anyList(), anyString());
      verify(communicationRepository, never()).save(any(Communication.class));
    }

    @Test
    @DisplayName("given_messageForInject_should_lookupAndPersistUsersOnlyInInjectTenant")
    void given_messageForInject_should_lookupAndPersistUsersOnlyInInjectTenant() throws Exception {
      // Arrange
      String tenantId = "tenant-a";
      String injectId = "inject-a";
      String messageId = "message-b";

      Inject inject = new Inject();
      inject.setId(injectId);
      inject.setTenant(new Tenant(tenantId));

      when(injectRepository.findById(anyString())).thenReturn(java.util.Optional.of(inject));
      when(communicationRepository.existsByIdentifierAndInjectTenantId(messageId, tenantId))
          .thenReturn(false);
      when(userRepository.findAllByEmailInIgnoreCaseAndTenantId(anyList(), eq(tenantId)))
          .thenReturn(java.util.List.of());

      Message[] messages = new Message[] {buildMessage(messageId, "[inject_id=" + injectId + "]")};

      // Act
      ReflectionTestUtils.invokeMethod(imapService, "parseMessages", messages, false);

      // Assert
      verify(communicationRepository).existsByIdentifierAndInjectTenantId(messageId, tenantId);
      verify(userRepository).findAllByEmailInIgnoreCaseAndTenantId(anyList(), eq(tenantId));
      verify(communicationRepository, never()).save(any(Communication.class));
    }

    @Test
    @DisplayName("given_footerBuiltByEmailContent_should_extractInjectIdOnly")
    void given_footerBuiltByEmailContent_should_extractInjectIdOnly() throws Exception {
      // Arrange
      String tenantId = "tenant-a";
      String injectId = "inject-a";
      String messageId = "message-d";

      Inject inject = new Inject();
      inject.setId(injectId);
      inject.setTenant(new Tenant(tenantId));

      // Build the footer exactly as production code does (EmailContent.buildMessage), instead of
      // hand-writing the string, so the test catches any future change to the footer format.
      ExecutableInject executableInject = mock(ExecutableInject.class);
      when(executableInject.getInjection()).thenReturn(inject);
      when(executableInject.isChainingExecution()).thenReturn(false);
      when(executableInject.isRuntime()).thenReturn(true);

      EmailContent emailContent = new EmailContent();
      emailContent.setBody("Hello team");
      String bodyWithFooter = emailContent.buildMessage(executableInject, "http://localhost:8080");

      when(injectRepository.findById(injectId)).thenReturn(java.util.Optional.of(inject));
      when(communicationRepository.existsByIdentifierAndInjectTenantId(messageId, tenantId))
          .thenReturn(false);
      when(userRepository.findAllByEmailInIgnoreCaseAndTenantId(anyList(), eq(tenantId)))
          .thenReturn(java.util.List.of());

      Message[] messages = new Message[] {buildMessage(messageId, bodyWithFooter)};

      // Act
      ReflectionTestUtils.invokeMethod(imapService, "parseMessages", messages, false);

      // Assert - the id extracted from the real footer must be exactly the inject id, not
      // polluted by the trailing "[base_url=...]" token that follows it in the same footer block
      verify(injectRepository).findById(injectId);
      verify(communicationRepository).existsByIdentifierAndInjectTenantId(messageId, tenantId);
    }

    @Test
    @DisplayName("given_twoInjectsFromDifferentTenantsWithSameEmail_should_linkOnlyTenantUser")
    void given_twoInjectsFromDifferentTenantsWithSameEmail_should_linkOnlyTenantUser()
        throws Exception {
      // Arrange
      String tenantA = "tenant-a";
      String tenantB = "tenant-b";
      String injectIdA = "inject-a";
      String injectIdB = "inject-b";
      String messageIdA = "message-a";
      String messageIdB = "message-b";

      Inject injectA = new Inject();
      injectA.setId(injectIdA);
      injectA.setTenant(new Tenant(tenantA));

      Inject injectB = new Inject();
      injectB.setId(injectIdB);
      injectB.setTenant(new Tenant(tenantB));

      io.openaev.database.model.User userA = new io.openaev.database.model.User();
      userA.setId("user-a");
      userA.setEmail("participant@acme.test");

      io.openaev.database.model.User userB = new io.openaev.database.model.User();
      userB.setId("user-b");
      userB.setEmail("participant@acme.test");

      when(injectRepository.findById(anyString()))
          .thenAnswer(
              invocation -> {
                String injectId = invocation.getArgument(0);
                if (injectId != null && injectId.contains(injectIdA)) {
                  return java.util.Optional.of(injectA);
                }
                if (injectId != null && injectId.contains(injectIdB)) {
                  return java.util.Optional.of(injectB);
                }
                return java.util.Optional.empty();
              });
      when(communicationRepository.existsByIdentifierAndInjectTenantId(anyString(), anyString()))
          .thenReturn(false);
      when(userRepository.findAllByEmailInIgnoreCaseAndTenantId(anyList(), anyString()))
          .thenAnswer(
              invocation -> {
                String tenantId = invocation.getArgument(1);
                if (tenantA.equals(tenantId)) {
                  return java.util.List.of(userA);
                }
                if (tenantB.equals(tenantId)) {
                  return java.util.List.of(userB);
                }
                return java.util.List.of();
              });
      when(communicationRepository.save(any(Communication.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      Message[] messages =
          new Message[] {
            buildMessage(messageIdA, "[inject_id=" + injectIdA + "]"),
            buildMessage(messageIdB, "[inject_id=" + injectIdB + "]")
          };

      // Act
      ReflectionTestUtils.invokeMethod(imapService, "parseMessages", messages, false);

      // Assert
      verify(userRepository, times(1))
          .findAllByEmailInIgnoreCaseAndTenantId(anyList(), eq(tenantA));
      verify(userRepository, times(1))
          .findAllByEmailInIgnoreCaseAndTenantId(anyList(), eq(tenantB));

      ArgumentCaptor<Communication> communicationCaptor =
          ArgumentCaptor.forClass(Communication.class);
      verify(communicationRepository, times(4)).save(communicationCaptor.capture());
      List<Communication> savedCommunications = communicationCaptor.getAllValues();

      List<Communication> tenantACommunications =
          savedCommunications.stream().filter(c -> messageIdA.equals(c.getIdentifier())).toList();
      List<Communication> tenantBCommunications =
          savedCommunications.stream().filter(c -> messageIdB.equals(c.getIdentifier())).toList();

      Assertions.assertFalse(tenantACommunications.isEmpty());
      Assertions.assertFalse(tenantBCommunications.isEmpty());
      Assertions.assertTrue(
          tenantACommunications.stream()
              .allMatch(
                  c ->
                      c.getUsers().size() == 1
                          && "user-a".equals(c.getUsers().getFirst().getId())));
      Assertions.assertTrue(
          tenantBCommunications.stream()
              .allMatch(
                  c ->
                      c.getUsers().size() == 1
                          && "user-b".equals(c.getUsers().getFirst().getId())));
    }
  }

  private MimeMessage buildMessage(String messageId, String body) throws Exception {
    String participantEmail = "participant@acme.test";
    Session session = Session.getInstance(new Properties());
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(participantEmail));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(participantEmail));
    message.setText(body);
    message.setSentDate(new Date());

    MimeMessage spy = org.mockito.Mockito.spy(message);
    doReturn(messageId).when(spy).getMessageID();
    lenient().doReturn(new Date()).when(spy).getSentDate();
    lenient().doReturn(new Date()).when(spy).getReceivedDate();
    return spy;
  }
}
