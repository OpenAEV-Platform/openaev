package io.openaev.aop.audit_log;

import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.ResourceType;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(
    properties = {"openaev.audit-logs.service.enabled=true", "openaev.audit-logs.log-reads=false"})
class AccessControlAuditLogAspectTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @SpyBean private AccessControlAuditLogger accessControlAuditLogger;

  @BeforeEach
  void setup() {
    reset(accessControlAuditLogger);
    doReturn(true).when(accessControlAuditLogger).isAuditLoggingEnabled();
  }

  @Nested
  @DisplayName("RBAC denials")
  class RbacDenialAudit {

    @Test
    @WithMockUser
    void given_missingCapability_should_logUnauthorizedEvent() throws Exception {
      // Arrange / Act
      mvc.perform(get(TEAM_URI)).andExpect(status().isForbidden());

      // Assert
      verify(accessControlAuditLogger, timeout(1000))
          .logAccessControlEvent(
              eq("unauthorized"),
              eq("error"),
              eq(ResourceType.TEAM),
              anyString(),
              any(),
              any(),
              any(),
              anyString());
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
      verify(accessControlAuditLogger, after(300).never())
          .logAccessControlEvent(
              anyString(), anyString(), any(), anyString(), any(), any(), any(), anyString());
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
      verify(accessControlAuditLogger, after(300).never())
          .logAccessControlEvent(
              anyString(), anyString(), any(), anyString(), any(), any(), any(), anyString());
    }
  }
}
