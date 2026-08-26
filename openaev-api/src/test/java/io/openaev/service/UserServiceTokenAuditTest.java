package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.openaev.IntegrationTest;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.config.ShutdownService;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
@DisplayName("Token creation audit (AC2)")
class UserServiceTokenAuditTest extends IntegrationTest {

  @Autowired private UserService userService;
  @Autowired private UserComposer userComposer;

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private ShutdownService shutdownService;

  @BeforeEach
  void setup() {
    reset(auditLogger, shutdownService);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    // Prevent System.exit() if a halt-on-failure path is ever hit during the async audit.
    Mockito.doNothing().when(shutdownService).initiateShutdown();
  }

  @Nested
  @DisplayName("createUserToken")
  @WithMockUser(isAdmin = true)
  class CreateUserToken {

    @Test
    @DisplayName("given_tokenCreation_should_logAuditEvent_withTokenIdCreatorAndTimestamp")
    void given_tokenCreation_should_logAuditEvent_withTokenIdCreatorAndTimestamp() {
      // Arrange
      User user =
          userComposer
              .forUser(UserFixture.getUser("Token", "Owner", "token-audit@test.invalid"))
              .persist()
              .get();

      // Act — token creation is the endpoint-agnostic choke point that must satisfy AC2, regardless
      // of the calling flow (user/player creation, service account, connector, renewal).
      Token created = userService.createUserToken(user);

      // Assert
      ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
      verify(auditLogger, timeout(2000)).logEvent(eventCaptor.capture());

      AuditEvent event = eventCaptor.getValue();
      assertThat(event.getEventType()).isEqualTo(EventType.MUTATION);
      assertThat(event.getEventScope()).isEqualTo(AuditEventScope.CREATE);
      assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
      assertThat(event.getResourceType()).isEqualTo(ResourceType.TOKEN);
      assertThat(event.getResourceId()).isEqualTo(created.getId());
      assertThat(event.getContextData())
          .containsEntry("token_id", created.getId())
          .containsEntry("token_user_id", user.getId())
          .containsKey("timestamp");
    }
  }
}
