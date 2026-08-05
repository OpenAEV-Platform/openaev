package io.openaev.aop.audit_log;

import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.AuditLogProperties;
import io.openaev.config.ShutdownService;
import io.openaev.database.audit.AuditLogContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.team.form.TeamUpdateInput;
import io.openaev.service.LogService;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.fixtures.EndpointRegisterInputFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Map;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
class AccessControlAuditLogAspectTest extends IntegrationTest {

  private static final String PROTECTED_TEAM_ID = "team-without-permission";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ExecutorComposer executorComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TestUserHolder testUserHolder;

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private AuditLogProperties auditLogProperties;
  @MockitoSpyBean private LogService logService;
  @MockitoSpyBean private ShutdownService shutdownService;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void setup() {
    reset(auditLogger, auditLogProperties, logService, shutdownService);
    doReturn(true).when(auditLogger).isAuditLoggingEnabled();
    // Always prevent System.exit() — initiateShutdown() starts a daemon thread that calls
    // System.exit(). Without this safety net, any test that accidentally triggers
    // prepareLogFailure() (e.g. via a race with the async audit thread) would kill the JVM.
    doNothing().when(shutdownService).initiateShutdown();
    // /register carries a TxCtx param (v2 write-scope resolution): the mock user needs a
    // users_tenants row, otherwise the scope resolves to TxCtx.Missing and the write is refused
    // with 400 regardless of isAdmin/capabilities. Guarded by an active-transaction check because
    // one test in this class runs with Propagation.NOT_SUPPORTED (no transaction during setup).
    if (testUserHolder.isSet()
        && org.springframework.transaction.support.TransactionSynchronizationManager
            .isActualTransactionActive()) {
      tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    }
  }

  @Nested
  @DisplayName("RBAC denials — audit event emitted for unauthorized access")
  class RbacDenialAudit {

    @Test
    @WithMockUser
    void given_missingCapability_should_returnForbiddenAndLogUnauthorizedEvent() throws Exception {
      // Arrange
      ArgumentCaptor<String> eventScopeCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> eventStatusCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<ResourceType> resourceTypeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);

      // Act
      mvc.perform(delete(TEAM_URI + "/{teamId}", PROTECTED_TEAM_ID).with(csrf()))
          .andExpect(status().isForbidden());

      // Assert — audit event logged with "unauthorized" scope
      verify(auditLogger, timeout(2000))
          .logAccessControlEvent(
              eventScopeCaptor.capture(),
              eventStatusCaptor.capture(),
              resourceTypeCaptor.capture(),
              resourceIdCaptor.capture(),
              any(),
              any(),
              any(),
              any(),
              anyString());

      assertThat(eventScopeCaptor.getValue()).isEqualTo("unauthorized");
      assertThat(eventStatusCaptor.getValue()).isEqualTo("error");
      assertThat(resourceTypeCaptor.getValue()).isEqualTo(ResourceType.TEAM);
      assertThat(resourceIdCaptor.getValue()).isEqualTo(PROTECTED_TEAM_ID);
    }

    @Test
    @WithMockUser
    void
        given_missingCapabilityWithAutomatedUserAgent_should_returnForbiddenAndLogUnauthorizedEvent()
            throws Exception {
      // Arrange
      ArgumentCaptor<String> eventScopeCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> eventStatusCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<ResourceType> resourceTypeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);

      // Act
      mvc.perform(
              delete(TEAM_URI + "/{teamId}", PROTECTED_TEAM_ID)
                  .with(csrf())
                  .header("User-Agent", "openaev-agent/x.x.x"))
          .andExpect(status().isForbidden());

      // Assert — audit event logged even for automated agents
      verify(auditLogger, timeout(2000))
          .logAccessControlEvent(
              eventScopeCaptor.capture(),
              eventStatusCaptor.capture(),
              resourceTypeCaptor.capture(),
              resourceIdCaptor.capture(),
              any(),
              any(),
              any(),
              any(),
              anyString());

      assertThat(eventScopeCaptor.getValue()).isEqualTo("unauthorized");
      assertThat(eventStatusCaptor.getValue()).isEqualTo("error");
      assertThat(resourceTypeCaptor.getValue()).isEqualTo(ResourceType.TEAM);
      assertThat(resourceIdCaptor.getValue()).isEqualTo(PROTECTED_TEAM_ID);
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

  @Nested
  @DisplayName("Create operations")
  class CreateOperationAudit {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS})
    void given_successfulCreate_should_logCreateEvent() throws Exception {
      // Arrange
      ArgumentCaptor<String> eventScopeCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> eventStatusCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<ResourceType> resourceTypeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      ArgumentCaptor<JsonNode> inputCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<JsonNode> outputCaptor = ArgumentCaptor.forClass(JsonNode.class);

      String teamJson = objectMapper.writeValueAsString(TeamFixture.createTeam());

      // Act
      mvc.perform(
              post(TEAM_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(teamJson))
          .andExpect(status().isOk());

      // Assert
      verify(auditLogger, timeout(1000))
          .logAccessControlEvent(
              eventScopeCaptor.capture(),
              eventStatusCaptor.capture(),
              resourceTypeCaptor.capture(),
              anyString(),
              inputCaptor.capture(),
              outputCaptor.capture(),
              any(),
              any(),
              anyString());

      assertThat(eventScopeCaptor.getValue()).isEqualTo("create");
      assertThat(eventStatusCaptor.getValue()).isEqualTo("success");
      assertThat(resourceTypeCaptor.getValue()).isEqualTo(ResourceType.TEAM);
      assertThat(inputCaptor.getValue()).isNotNull();
      assertThat(outputCaptor.getValue()).isNotNull();
    }
  }

  @Nested
  @DisplayName("Delete operations")
  class DeleteOperationAudit {

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TEAMS_AND_PLAYERS})
    void given_successfulDelete_should_logDeleteEvent() throws Exception {
      // Arrange
      Team team = teamRepository.save(TeamFixture.getDefaultTeam());
      entityManager.flush();

      ArgumentCaptor<String> eventScopeCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> eventStatusCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<ResourceType> resourceTypeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);

      // Act
      mvc.perform(delete(TEAM_URI + "/{teamId}", team.getId()).with(csrf()))
          .andExpect(status().isOk());

      // Assert
      verify(auditLogger, timeout(1000))
          .logAccessControlEvent(
              eventScopeCaptor.capture(),
              eventStatusCaptor.capture(),
              resourceTypeCaptor.capture(),
              resourceIdCaptor.capture(),
              any(),
              any(),
              any(),
              any(),
              anyString());

      assertThat(eventScopeCaptor.getValue()).isEqualTo("delete");
      assertThat(eventStatusCaptor.getValue()).isEqualTo("success");
      assertThat(resourceTypeCaptor.getValue()).isEqualTo(ResourceType.TEAM);
      assertThat(resourceIdCaptor.getValue()).isEqualTo(team.getId());
    }
  }

  @Nested
  @DisplayName("Audit logging disabled")
  class AuditDisabled {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS})
    void given_auditDisabled_should_notLogEvent() throws Exception {
      // Arrange
      doReturn(false).when(auditLogger).isAuditLoggingEnabled();

      String teamJson = objectMapper.writeValueAsString(TeamFixture.createTeam());

      // Act
      mvc.perform(
              post(TEAM_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(teamJson))
          .andExpect(status().isOk());

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

    @Test
    @WithMockUser(isAdmin = true)
    void given_auditContextDisabled_should_notLogSuccessEvent() throws Exception {
      // Arrange — set up executor and service account required for agent registration
      executorComposer.reset();
      executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
      serviceAccountPrivilegeService.ensurePrivilegedUserExists(
          io.openaev.context.TenantContext.getCurrentTenant());
      entityManager.flush();

      // Register the same agent twice; the second registration is a heartbeat
      String registerJson =
          objectMapper.writeValueAsString(
              EndpointRegisterInputFixture.getDefaultEndpointRegisterInput());

      // First registration — creates the agent (audit IS expected)
      mvc.perform(
              post(ENDPOINT_URI + "/register")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(registerJson))
          .andExpect(status().isOk());

      // Wait for first audit event and then reset the spy
      verify(auditLogger, timeout(1000))
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
      reset(auditLogger);
      doReturn(true).when(auditLogger).isAuditLoggingEnabled();

      // Act — second registration with same data (heartbeat, no significant change)
      mvc.perform(
              post(ENDPOINT_URI + "/register")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(registerJson))
          .andExpect(status().isOk());

      // Assert — audit should be suppressed for the heartbeat
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
  @DisplayName("Update operations")
  class UpdateOperationAudit {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS})
    void given_successfulUpdate_should_logUpdateEventWithInputAndResourceId() throws Exception {
      // Arrange
      Team team = teamRepository.save(TeamFixture.getDefaultTeam());
      entityManager.flush();

      TeamUpdateInput updateInput = new TeamUpdateInput();
      updateInput.setName("Updated Team Name");
      updateInput.setDescription("Updated description");

      String updateJson = objectMapper.writeValueAsString(updateInput);

      ArgumentCaptor<String> eventScopeCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> eventStatusCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<ResourceType> resourceTypeCaptor = ArgumentCaptor.forClass(ResourceType.class);
      ArgumentCaptor<String> resourceIdCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<JsonNode> inputCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<JsonNode> outputCaptor = ArgumentCaptor.forClass(JsonNode.class);
      ArgumentCaptor<JsonNode> signatureCaptor = ArgumentCaptor.forClass(JsonNode.class);

      // Act
      mvc.perform(
              put(TEAM_URI + "/{teamId}", team.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(updateJson))
          .andExpect(status().isOk());

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
              any(),
              anyString());

      assertThat(eventScopeCaptor.getValue()).isEqualTo("update");
      assertThat(eventStatusCaptor.getValue()).isEqualTo("success");
      assertThat(resourceTypeCaptor.getValue()).isEqualTo(ResourceType.TEAM);
      assertThat(resourceIdCaptor.getValue()).isEqualTo(team.getId());
      assertThat(inputCaptor.getValue()).isNotNull();
      assertThat(inputCaptor.getValue().path("team_name").asText()).isEqualTo("Updated Team Name");
      assertThat(outputCaptor.getValue()).isNotNull();
      assertThat(signatureCaptor.getValue()).isNotNull();
      assertThat(signatureCaptor.getValue().path("method").asText()).contains("TeamApi.updateTeam");
    }
  }

  @Nested
  @DisplayName("Business error operations")
  class BusinessErrorAudit {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS})
    void given_nonExistentTeamUpdate_should_logErrorEvent() throws Exception {
      // Arrange
      String nonExistentId = "non-existent-team-id";

      TeamUpdateInput updateInput = new TeamUpdateInput();
      updateInput.setName("Updated Team");
      String updateJson = objectMapper.writeValueAsString(updateInput);

      ArgumentCaptor<String> eventScopeCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> eventStatusCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<JsonNode> outputCaptor = ArgumentCaptor.forClass(JsonNode.class);

      // Act
      mvc.perform(
              put(TEAM_URI + "/{teamId}", nonExistentId)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(updateJson))
          .andExpect(status().isNotFound());

      // Assert
      verify(auditLogger, timeout(1000))
          .logAccessControlEvent(
              eventScopeCaptor.capture(),
              eventStatusCaptor.capture(),
              any(),
              anyString(),
              any(),
              outputCaptor.capture(),
              any(),
              any(),
              anyString());

      assertThat(eventScopeCaptor.getValue()).isEqualTo("update");
      assertThat(eventStatusCaptor.getValue()).isEqualTo("error");
      assertThat(outputCaptor.getValue()).isNotNull();
      assertThat(outputCaptor.getValue().has("exception_type")).isTrue();
    }
  }

  @Nested
  @DisplayName("Entity snapshot capture (captureEntitySnapshots)")
  class EntityDiffCapture {

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TEAMS_AND_PLAYERS})
    void given_entityDiffsInContext_should_serializeAndPassToAuditLogger() throws Exception {
      // Arrange — pre-populate AuditLogContext via request attributes (the storage used during
      // HTTP requests) to simulate snapshots stored by @PreUpdate listener
      Team team = teamRepository.save(TeamFixture.getDefaultTeam());
      entityManager.flush();

      Map<String, AuditLogContext.EntitySnapshot> snapshotsMap = new java.util.LinkedHashMap<>();
      snapshotsMap.put(
          team.getId(),
          new AuditLogContext.EntitySnapshot(
              "Team", "update", Map.of("name", "Old Name"), Map.of("name", "New Name")));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, AuditLogContext.EntitySnapshot>> snapshotsCaptor =
          ArgumentCaptor.forClass(Map.class);

      // Act — pass snapshots as request attribute so AuditLogContext finds them during the
      // request
      mvc.perform(
              delete(TEAM_URI + "/{teamId}", team.getId())
                  .with(csrf())
                  .requestAttr("openaev.audit.entitySnapshots", snapshotsMap))
          .andExpect(status().isOk());

      // Assert — captureEntitySnapshots() should pass the pre-stored snapshots
      verify(auditLogger, timeout(1000))
          .logAccessControlEvent(
              anyString(),
              anyString(),
              any(),
              anyString(),
              any(),
              any(),
              any(),
              snapshotsCaptor.capture(),
              anyString());

      Map<String, AuditLogContext.EntitySnapshot> captured = snapshotsCaptor.getValue();
      assertThat(captured).isNotNull();
      assertThat(captured).containsKey(team.getId());
      AuditLogContext.EntitySnapshot snapshot = captured.get(team.getId());
      assertThat(snapshot.entityType()).isEqualTo("Team");
      assertThat(snapshot.operation()).isEqualTo("update");
      assertThat(snapshot.before().get("name")).isEqualTo("Old Name");
      assertThat(snapshot.after().get("name")).isEqualTo("New Name");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS})
    void given_noDiffsInContext_should_passEmptyEntityDiffs() throws Exception {
      // Arrange — no snapshots in AuditLogContext
      String teamJson = objectMapper.writeValueAsString(TeamFixture.createTeam());

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, AuditLogContext.EntitySnapshot>> snapshotsCaptor =
          ArgumentCaptor.forClass(Map.class);

      // Act
      mvc.perform(
              post(TEAM_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(teamJson))
          .andExpect(status().isOk());

      // Assert
      verify(auditLogger, timeout(1000))
          .logAccessControlEvent(
              anyString(),
              anyString(),
              any(),
              anyString(),
              any(),
              any(),
              any(),
              snapshotsCaptor.capture(),
              anyString());

      assertThat(snapshotsCaptor.getValue()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Halt-on-failure: rollback on audit transport failure")
  class HaltOnFailureRollback {

    private void stubHaltOnFailure() {
      doReturn(true).when(auditLogProperties).isHaltOnFailure();
      doReturn(true).when(logService).isEnabled();
      doReturn(false)
          .when(logService)
          .logRequestEvent(
              anyString(),
              anyString(),
              any(),
              anyString(),
              any(),
              any(),
              any(),
              any(),
              any(Level.class),
              anyString());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @WithMockUser(withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS})
    void given_haltOnFailureAndTransportFails_should_returnServerErrorAndRollback()
        throws Exception {
      // Arrange — enable halt-on-failure and make transport fail
      stubHaltOnFailure();
      long teamCountBefore = teamRepository.count();

      String teamJson = objectMapper.writeValueAsString(TeamFixture.createTeam());

      // Act — request should fail (audit failure propagates through @Transactional boundary)
      mvc.perform(
              post(TEAM_URI).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(teamJson))
          .andExpect(status().isInternalServerError());

      // Assert — the audit transport was attempted and halt-on-failure triggered shutdown
      verify(logService, timeout(2000))
          .logRequestEvent(
              anyString(),
              anyString(),
              any(),
              anyString(),
              any(),
              any(),
              any(),
              any(),
              any(Level.class),
              anyString());
      verify(shutdownService, timeout(2000)).initiateShutdown();

      // Assert — the team creation was rolled back: no new rows in the table
      assertThat(teamRepository.count()).isEqualTo(teamCountBefore);
    }
  }
}
