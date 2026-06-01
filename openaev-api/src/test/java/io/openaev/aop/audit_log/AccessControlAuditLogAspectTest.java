package io.openaev.aop.audit_log;

import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.ResourceType;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
class AccessControlAuditLogAspectTest extends IntegrationTest {

  private static final String PROTECTED_TEAM_ID = "team-without-permission";

  @Autowired private MockMvc mvc;

  @MockitoSpyBean private AuditLogger auditLogger;

  @BeforeEach
  void setup() {
    reset(auditLogger);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
  }

  @Nested
  @DisplayName("RBAC denials")
  class RbacDenialAudit {

    @Test
    @WithMockUser
    void given_missingCapability_should_logUnauthorizedEvent() throws Exception {
      // Arrange
      ArgumentCaptor<String> eventScopeCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> eventStatusCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<ResourceType> resourceTypeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<JsonNode> inputCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<JsonNode> outputCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<JsonNode> signatureCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<JsonNode> entityDiffsCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<String> logUuidCaptor = ArgumentCaptor.forClass(String.class);

      // Act
      mvc.perform(delete(TEAM_URI + "/{teamId}", PROTECTED_TEAM_ID).with(csrf()))
          .andExpect(status().isForbidden());

      // Assert
      verify(auditLogger, timeout(1000))
          .logAccessControlEvent(
              eventScopeCaptor.capture(),
              eventStatusCaptor.capture(),
              resourceTypeCaptor.capture(),
              resourceIdCaptor.capture(),
              inputCaptor.capture(),
              outputCaptor.capture(),
              signatureCaptor.capture(),
              entityDiffsCaptor.capture(),
              logUuidCaptor.capture());

      assertThat(eventScopeCaptor.getValue()).isEqualTo("unauthorized");
      assertThat(eventStatusCaptor.getValue()).isEqualTo("error");
      assertThat(resourceTypeCaptor.getValue()).isEqualTo(ResourceType.TEAM);
      assertThat(resourceIdCaptor.getValue()).isEqualTo(PROTECTED_TEAM_ID);
      assertThat(inputCaptor.getValue()).isNull();
      assertThat(outputCaptor.getValue()).isNotNull();
      assertThat(outputCaptor.getValue().path("exception_type").asText())
          .contains("AccessControlAspect$");
      assertThat(signatureCaptor.getValue()).isNotNull();
      assertThat(logUuidCaptor.getValue()).isNotBlank();
    }

    @Test
    @WithMockUser
    void given_missingCapabilityWithAutomatedUserAgent_should_logUnauthorizedEvent()
        throws Exception {
      // Arrange
      ArgumentCaptor<String> eventScopeCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> eventStatusCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<ResourceType> resourceTypeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<JsonNode> inputCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<JsonNode> outputCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<JsonNode> signatureCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<String> logUuidCaptor = ArgumentCaptor.forClass(String.class);

      // Act
      mvc.perform(
              delete(TEAM_URI + "/{teamId}", PROTECTED_TEAM_ID)
                  .with(csrf())
                  .header("User-Agent", "openaev-agent/x.x.x"))
          .andExpect(status().isForbidden());

      // Assert
      verify(auditLogger, timeout(1000))
          .logAccessControlEvent(
              eventScopeCaptor.capture(),
              eventStatusCaptor.capture(),
              resourceTypeCaptor.capture(),
              resourceIdCaptor.capture(),
              inputCaptor.capture(),
              outputCaptor.capture(),
              signatureCaptor.capture(),
              logUuidCaptor.capture());

      assertThat(eventScopeCaptor.getValue()).isEqualTo("unauthorized");
      assertThat(eventStatusCaptor.getValue()).isEqualTo("error");
      assertThat(resourceTypeCaptor.getValue()).isEqualTo(ResourceType.TEAM);
      assertThat(resourceIdCaptor.getValue()).isEqualTo(PROTECTED_TEAM_ID);
      assertThat(inputCaptor.getValue()).isNull();
      assertThat(outputCaptor.getValue()).isNotNull();
      assertThat(outputCaptor.getValue().path("exception_type").asText())
          .contains("AccessControlAspect$");
      assertThat(signatureCaptor.getValue()).isNotNull();
      assertThat(logUuidCaptor.getValue()).isNotBlank();
    }
  }

  @Nested
  @DisplayName("Unauthenticated requests")
  class UnauthenticatedRequestAudit {

    @Test
    void given_unauthenticatedRequest_should_notLogUnauthorizedEvent() throws Exception {
      // Arrange / Act
      mvc.perform(get(TEAM_URI)).andExpect(status().isUnauthorized());

      // Assert
      verify(auditLogger, after(1000).never())
          .logAccessControlEvent(
              anyString(),
              anyString(),
              any(),
              anyString(),
              any(),
              any(),
              any(),
              any(),
              anyString());
    }
  }

  @Nested
  @DisplayName("Read operations with read logging disabled")
  class ReadOperationAudit {

    @Test
    @WithMockUser(withCapabilities = {Capability.ACCESS_TEAMS_AND_PLAYERS})
    void given_successfulRead_should_notLogEvent() throws Exception {
      // Arrange / Act
      mvc.perform(get(TEAM_URI)).andExpect(status().isOk());

      // Assert
      verify(auditLogger, after(1000).never())
          .logAccessControlEvent(
              anyString(),
              anyString(),
              any(),
              anyString(),
              any(),
              any(),
              any(),
              any(),
              anyString());
    }
  }
}
