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

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
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
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
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
  @Mock private TenantScopedTransaction tenantScopedTransaction;

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
            settingRepository,
            tenantScopedTransaction);
    ReflectionTestUtils.setField(imapService, "username", "mailbox@openaev.test");
    // Pass-through, so the body runs. What scope each call receives is asserted by the
    // ScopeArguments tests below: the real-stack test cannot tell forTenant from allTenants,
    // because every read after the resolve is keyed by a globally unique id.
    lenient()
        .when(tenantScopedTransaction.execute(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
    lenient()
        .doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantScopedTransaction)
        .execute(any(TxCtx.class), any(Runnable.class));
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
      String injectId = "83075dd4-60a4-4307-aa3a-19ee673125f1";
      String messageId = "message-a";

      Inject inject = new Inject();
      inject.setId(injectId);
      inject.setTenant(new Tenant(tenantId));

      when(injectRepository.findTenantRefById(injectId))
          .thenReturn(java.util.Optional.of(tenantRef(injectId, tenantId)));
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
      String injectId = "9f1c2a3b-4d5e-4f60-8a71-1234567890ab";
      String messageId = "message-b";

      Inject inject = new Inject();
      inject.setId(injectId);
      inject.setTenant(new Tenant(tenantId));

      when(injectRepository.findTenantRefById(anyString()))
          .thenReturn(java.util.Optional.of(tenantRef(inject.getId(), tenantId)));
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
      // Inject ids are real UUIDs in production (see Inject#id); use one here so the test exercises
      // the actual anchored UUID regex in ImapService.INJECT_ID_PATTERN, not a fake id format.
      String injectId = "83075dd4-60a4-4307-aa3a-19ee673125f1";
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

      when(injectRepository.findTenantRefById(injectId))
          .thenReturn(java.util.Optional.of(tenantRef(injectId, tenantId)));
      when(communicationRepository.existsByIdentifierAndInjectTenantId(messageId, tenantId))
          .thenReturn(false);
      when(userRepository.findAllByEmailInIgnoreCaseAndTenantId(anyList(), eq(tenantId)))
          .thenReturn(java.util.List.of());

      Message[] messages = new Message[] {buildMessage(messageId, bodyWithFooter)};

      // Act
      ReflectionTestUtils.invokeMethod(imapService, "parseMessages", messages, false);

      // Assert - the id extracted from the real footer must be exactly the inject id, not
      // polluted by the trailing "[base_url=...]" token that follows it in the same footer block
      verify(injectRepository).findTenantRefById(injectId);
      verify(communicationRepository).existsByIdentifierAndInjectTenantId(messageId, tenantId);
    }

    @Test
    @DisplayName("given_twoInjectsFromDifferentTenantsWithSameEmail_should_linkOnlyTenantUser")
    void given_twoInjectsFromDifferentTenantsWithSameEmail_should_linkOnlyTenantUser()
        throws Exception {
      // Arrange
      String tenantA = "tenant-a";
      String tenantB = "tenant-b";
      String injectIdA = "83075dd4-60a4-4307-aa3a-19ee673125f1";
      String injectIdB = "9f1c2a3b-4d5e-4f60-8a71-1234567890ab";
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
      when(injectRepository.findTenantRefById(anyString()))
          .thenAnswer(
              invocation -> {
                String injectId = invocation.getArgument(0);
                if (injectId != null && injectId.contains(injectIdA)) {
                  return java.util.Optional.of(tenantRef(injectIdA, tenantA));
                }
                if (injectId != null && injectId.contains(injectIdB)) {
                  return java.util.Optional.of(tenantRef(injectIdB, tenantB));
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
      java.util.Map<String, Communication> savedById = new java.util.HashMap<>();
      when(communicationRepository.save(any(Communication.class)))
          .thenAnswer(
              invocation -> {
                Communication saved = invocation.getArgument(0);
                if (saved.getId() == null) {
                  saved.setId(UUID.randomUUID().toString());
                }
                savedById.put(saved.getId(), saved);
                return saved;
              });
      when(communicationRepository.findById(anyString()))
          .thenAnswer(
              invocation ->
                  java.util.Optional.ofNullable(savedById.get(invocation.<String>getArgument(0))));

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

  @Nested
  @DisplayName("Scope arguments: which TxCtx each unit of work receives")
  class ScopeArguments {

    /**
     * The resolve has to look across tenants, because an inbound mail carries no tenant. Everything
     * after it must run in the inject's own tenant. Nothing else pins that narrowing: every read
     * and write after the resolve is keyed by a globally unique id or filters on the tenant itself,
     * so widening the scope to allTenants leaves the recorded data identical and every other test
     * here and on the real stack stays green.
     */
    @Test
    @DisplayName("given_aReply_should_resolveAcrossTenantsThenWorkInTheInjectTenantOnly")
    void given_aReply_should_resolveAcrossTenantsThenWorkInTheInjectTenantOnly() throws Exception {
      // Arrange
      String injectId = "0b1c2d3e-4f50-4617-8829-aabbccddeeff";
      String tenantId = "tenant-scope-args";
      String messageId = "message-scope-args";
      Inject inject = new Inject();
      inject.setId(injectId);
      inject.setTenant(new Tenant(tenantId));
      when(injectRepository.findTenantRefById(injectId))
          .thenReturn(java.util.Optional.of(tenantRef(injectId, tenantId)));
      when(communicationRepository.existsByIdentifierAndInjectTenantId(messageId, tenantId))
          .thenReturn(true);
      Message[] messages = new Message[] {buildMessage(messageId, "[inject_id=" + injectId + "]")};

      // Act
      ReflectionTestUtils.invokeMethod(imapService, "parseMessages", messages, false);

      // Assert
      ArgumentCaptor<TxCtx> scopes = ArgumentCaptor.forClass(TxCtx.class);
      verify(tenantScopedTransaction, Mockito.atLeastOnce())
          .execute(scopes.capture(), any(Supplier.class));
      List<TxCtx> used = scopes.getAllValues();
      Assertions.assertEquals(
          TxCtx.allTenants(),
          used.get(0),
          "the resolve must look across tenants: an inbound mail names no tenant");
      Assertions.assertTrue(
          used.subList(1, used.size()).stream().allMatch(c -> c.equals(TxCtx.forTenant(tenantId))),
          "every unit of work after the resolve must be narrowed to the inject's own tenant, not"
              + " left at allTenants. Got: "
              + used);
    }
  }

  /** The projection the cross-tenant resolve reads, which carries two columns and no entity. */
  private static InjectRepository.InjectTenantRef tenantRef(String id, String tenantId) {
    return new InjectRepository.InjectTenantRef() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public String getTenantId() {
        return tenantId;
      }
    };
  }
}
