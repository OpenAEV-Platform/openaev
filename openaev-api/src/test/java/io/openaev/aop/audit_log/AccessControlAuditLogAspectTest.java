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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.audit.EntityDiffContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Team;
import io.openaev.database.repository.TeamRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.team.form.TeamUpdateInput;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
class AccessControlAuditLogAspectTest extends IntegrationTest {

  private static final String PROTECTED_TEAM_ID = "team-without-permission";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TeamRepository teamRepository;

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

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
  @DisplayName("Entity diff capture (captureEntityDiffs)")
  class EntityDiffCapture {

    @Test
    @WithMockUser(withCapabilities = {Capability.DELETE_TEAMS_AND_PLAYERS})
    void given_entityDiffsInContext_should_serializeAndPassToAuditLogger() throws Exception {
      // Arrange — pre-populate EntityDiffContext via request attributes (the storage used during
      // HTTP requests) to simulate diffs stored by @PreUpdate listener
      Team team = teamRepository.save(TeamFixture.getDefaultTeam());
      entityManager.flush();

      Map<String, EntityDiffContext.EntityDiff> diffsMap = new java.util.LinkedHashMap<>();
      diffsMap.put(
          team.getId(),
          new EntityDiffContext.EntityDiff(
              "Team",
              "update",
              List.of(new EntityDiffContext.Change("name", "Old Name", "New Name"))));

      ArgumentCaptor<JsonNode> entityDiffsCaptor = ArgumentCaptor.forClass(JsonNode.class);

      // Act — pass diffs as request attribute so EntityDiffContext finds them during the request
      mvc.perform(
              delete(TEAM_URI + "/{teamId}", team.getId())
                  .with(csrf())
                  .requestAttr("openaev.audit.entityDiffs", diffsMap))
          .andExpect(status().isOk());

      // Assert — captureEntityDiffs() should serialize the pre-stored diffs
      verify(auditLogger, timeout(1000))
          .logAccessControlEvent(
              anyString(),
              anyString(),
              any(),
              anyString(),
              any(),
              any(),
              any(),
              entityDiffsCaptor.capture(),
              anyString());

      JsonNode entityDiffs = entityDiffsCaptor.getValue();
      assertThat(entityDiffs).isNotNull();
      assertThat(entityDiffs.isArray()).isTrue();
      assertThat(entityDiffs.size()).isEqualTo(1);

      JsonNode firstDiff = entityDiffs.get(0);
      assertThat(firstDiff.path("id").asText()).isEqualTo(team.getId());
      assertThat(firstDiff.path("entity_type").asText()).isEqualTo("Team");
      assertThat(firstDiff.path("operation").asText()).isEqualTo("update");
      assertThat(firstDiff.path("changes").isArray()).isTrue();
      assertThat(firstDiff.path("changes").get(0).path("field").asText()).isEqualTo("name");
      assertThat(firstDiff.path("changes").get(0).path("old_value").asText()).isEqualTo("Old Name");
      assertThat(firstDiff.path("changes").get(0).path("new_value").asText()).isEqualTo("New Name");
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS})
    void given_noDiffsInContext_should_passNullEntityDiffs() throws Exception {
      // Arrange — no diffs in EntityDiffContext
      String teamJson = objectMapper.writeValueAsString(TeamFixture.createTeam());

      ArgumentCaptor<JsonNode> entityDiffsCaptor = ArgumentCaptor.forClass(JsonNode.class);

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
              entityDiffsCaptor.capture(),
              anyString());

      assertThat(entityDiffsCaptor.getValue()).isNull();
    }
  }
}
