package io.openaev.api.threat_arsenal;

import static io.openaev.api.catalog.CatalogRoutes.TENANT_CATALOG_URI;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.service.payload.CustomizableField.LOCKED_ON_CUSTOMIZED_COPY;
import static io.openaev.service.payload.PayloadCustomizationMerge.CONFLICT_WITHOUT_CHOICE;
import static io.openaev.service.payload.PayloadCustomizationService.NOT_CUSTOMIZED;
import static io.openaev.service.payload.PayloadCustomizationService.NOT_FILIGRAN;
import static io.openaev.service.payload.PayloadCustomizationService.NOT_LATEST;
import static io.openaev.service.payload.PayloadCustomizationService.REVOKED;
import static io.openaev.service.payload.PayloadCustomizationService.UPDATE_CHANGED;
import static io.openaev.utils.StringUtils.CUSTOMIZED_SUFFIX;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadVersion;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.releasedPayload;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.syncRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Command;
import io.openaev.database.model.Domain;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.PayloadRevocationRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogReleaseFixture.ReleasedPayload;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=CATALOG")
@DisplayName("Customized copies of Filigran payloads")
class ThreatArsenalCustomizationApiTest extends IntegrationTest {

  private static final ReleasedPayload RELEASED = releasedPayload();
  private static final String FIRST = "1.0.0";
  private static final String NEWER = "1.1.0";
  private static final String MY_COMMAND = "sc create Crowdoor start= demand";
  private static final String FILIGRAN_COMMAND = "sc create Crowdoor start= delayed-auto";
  private static final String FILIGRAN_CLEANUP = "sc.exe stop Crowdoor && sc.exe delete Crowdoor";
  private static final String NEWER_CHANGELOG = "The service now starts after boot.";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private PayloadRevocationRepository payloadRevocationRepository;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;

  @BeforeEach
  void publishTwoVersions() throws Exception {
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
    injectorFixture.getWellKnownOaevImplantInjector();
    mvc.perform(
            syncRequest(
                    mapper.writeValueAsBytes(
                        manifest(
                            "1.261109.0",
                            List.of(),
                            List.of(
                                payloadVersion(FIRST, "VERIFIED", RELEASED),
                                withChangelog(payloadVersion(NEWER, "VERIFIED", RELEASED))))),
                    Map.of(
                        payloadFileName(FIRST), RELEASED.file(),
                        payloadFileName(NEWER), RELEASED.file()),
                    TENANT_CATALOG_URI + "/sync",
                    DEFAULT_TENANT_UUID)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  // -- HELPERS --

  private static Map<String, Object> withChangelog(Map<String, Object> version) {
    version.put("changelog", NEWER_CHANGELOG);
    return version;
  }

  /** Installs a released version: its payload id. */
  private String install(String version) throws Exception {
    ResultActions install =
        mvc.perform(
                post(
                        TENANT_CATALOG_URI + "/payloads/{externalId}/versions/{version}/install",
                        DEFAULT_TENANT_UUID,
                        PAYLOAD_ID,
                        version)
                    .with(csrf()))
            .andExpect(status().isCreated());
    entityManager.flush();
    return body(install).path("installed_id").asText();
  }

  /** The newer Filigran version, with the commands Filigran changed in it. */
  private String installNewerWithFiligranChanges() throws Exception {
    String newer = install(NEWER);
    // A released version is read-only: its content is set as another release would bring it.
    entityManager
        .createNativeQuery(
            "UPDATE payloads SET command_content = :content, payload_cleanup_command = :cleanup"
                + " WHERE payload_id = :id")
        .setParameter("content", FILIGRAN_COMMAND)
        .setParameter("cleanup", FILIGRAN_CLEANUP)
        .setParameter("id", newer)
        .executeUpdate();
    entityManager.clear();
    return newer;
  }

  private JsonNode customize(String payloadId) throws Exception {
    return body(
        mvc.perform(
                post(
                        TENANT_THREAT_ARSENAL_URL + "/{actionId}/customize",
                        DEFAULT_TENANT_UUID,
                        contractOf(payloadId).getId())
                    .with(csrf()))
            .andExpect(status().isCreated()));
  }

  /** The copy's command changed as the user would, without a new version. */
  private void changeCommand(String copyId, String content) {
    Command copy = (Command) Hibernate.unproxy(payload(copyId));
    copy.setContent(content);
    payloadRepository.save(copy);
    entityManager.flush();
    entityManager.clear();
  }

  private ResultActions customization(String actionId) throws Exception {
    return mvc.perform(
        get(
            TENANT_THREAT_ARSENAL_URL + "/{actionId}/customization",
            DEFAULT_TENANT_UUID,
            actionId));
  }

  private ResultActions update(String actionId, String targetId, Map<String, String> choices)
      throws Exception {
    return mvc.perform(
        post(
                TENANT_THREAT_ARSENAL_URL + "/{actionId}/customization/update",
                DEFAULT_TENANT_UUID,
                actionId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                mapper.writeValueAsString(
                    Map.of("update_payload_id", targetId, "update_choices", choices))));
  }

  /** What the action form sends for the copy, with the command and description given. */
  private ThreatArsenalActionUpdateInput edit(String copyId, String content, String description) {
    Command copy = (Command) Hibernate.unproxy(payload(copyId));
    InjectorContract action = contractOf(copyId);
    return new ThreatArsenalActionUpdateInput(
        copy.getName(),
        copy.getPlatforms(),
        description,
        copy.getExecutor(),
        content,
        copy.getExecutionArch(),
        copy.getExpectations(),
        copy.getExpectedSecurityPlatforms(),
        null,
        null,
        null,
        new ArrayList<>(copy.getArguments()),
        new ArrayList<>(copy.getPrerequisites()),
        copy.getCleanupExecutor(),
        copy.getCleanupCommand(),
        List.of(),
        action.getAttackPatterns().stream().map(AttackPattern::getId).toList(),
        null,
        null,
        action.getDomains().stream().map(Domain::getId).toList());
  }

  private Payload payload(String payloadId) {
    return payloadRepository.findById(payloadId).orElseThrow();
  }

  private InjectorContract contractOf(String payloadId) {
    return injectorContractRepository
        .findInjectorContractByPayload(payload(payloadId))
        .orElseThrow();
  }

  private JsonNode body(ResultActions result) throws Exception {
    return mapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  @Nested
  @DisplayName("Customize")
  class Customize {

    @Test
    @DisplayName("creates a local copy linked to the Filigran version")
    void given_filigranVersion_should_createALinkedCopy() throws Exception {
      // Arrange
      String base = install(FIRST);

      // Act
      JsonNode copy = customize(base);

      // Assert
      assertThat(copy.path("action_payload_customized").asBoolean()).isTrue();
      assertThat(copy.path("action_payload_managed").asBoolean()).isFalse();
      assertThat(copy.path("action_payload_version").asText()).isEqualTo(FIRST);
      Payload saved = payload(copy.path("action_payload").path("payload_id").asText());
      assertThat(saved.getBase().getId()).isEqualTo(base);
      assertThat(saved.getName()).isEqualTo(payload(base).getName() + CUSTOMIZED_SUFFIX);
      assertThat(saved.isManaged()).isFalse();
    }

    @Test
    @DisplayName("refuses a local payload")
    void given_localPayload_should_refuseIt() throws Exception {
      // Arrange
      InjectorContract local =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
              .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
              .persist()
              .get();
      entityManager.flush();

      // Act & Assert
      mvc.perform(
              post(
                      TENANT_THREAT_ARSENAL_URL + "/{actionId}/customize",
                      DEFAULT_TENANT_UUID,
                      local.getId())
                  .with(csrf()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(NOT_FILIGRAN));
    }

    @Test
    @DisplayName("refuses a revoked version")
    void given_revokedVersion_should_refuseIt() throws Exception {
      // Arrange
      String base = install(FIRST);
      payloadRevocationRepository.save(PayloadRevocation.byCatalogue(payload(base)));
      entityManager.flush();
      entityManager.clear();

      // Act & Assert
      mvc.perform(
              post(
                      TENANT_THREAT_ARSENAL_URL + "/{actionId}/customize",
                      DEFAULT_TENANT_UUID,
                      contractOf(base).getId())
                  .with(csrf()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(REVOKED));
    }
  }

  @Nested
  @DisplayName("Edit")
  class Edit {

    @Test
    @DisplayName("saves a changed command as a new version of the copy, still linked")
    void given_changedCommand_should_addAVersionOfTheCopy() throws Exception {
      // Arrange
      String base = install(FIRST);
      JsonNode copy = customize(base);
      String copyId = copy.path("action_payload").path("payload_id").asText();
      entityManager.flush();
      entityManager.clear();

      // Act
      JsonNode saved =
          body(
              mvc.perform(
                      put(
                              TENANT_THREAT_ARSENAL_URL + "/{actionId}",
                              DEFAULT_TENANT_UUID,
                              copy.path("injector_contract_id").asText())
                          .with(csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(mapper.writeValueAsString(edit(copyId, MY_COMMAND, ""))))
                  .andExpect(status().isOk()));

      // Assert
      Payload version = payload(saved.path("action_payload").path("payload_id").asText());
      assertThat(version.getId()).isNotEqualTo(copyId);
      assertThat(version.getVersion()).isEqualTo("1.1.0");
      assertThat(version.getBase().getId()).isEqualTo(base);
      assertThat(((Command) Hibernate.unproxy(version)).getContent()).isEqualTo(MY_COMMAND);
    }

    @Test
    @DisplayName("refuses a change outside the fields a copy may change")
    void given_changedDescription_should_refuseIt() throws Exception {
      // Arrange
      JsonNode copy = customize(install(FIRST));
      String copyId = copy.path("action_payload").path("payload_id").asText();
      Command current = (Command) Hibernate.unproxy(payload(copyId));
      entityManager.flush();
      entityManager.clear();

      // Act & Assert
      mvc.perform(
              put(
                      TENANT_THREAT_ARSENAL_URL + "/{actionId}",
                      DEFAULT_TENANT_UUID,
                      copy.path("injector_contract_id").asText())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          edit(copyId, current.getContent(), "My own description"))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(LOCKED_ON_CUSTOMIZED_COPY));
    }
  }

  @Nested
  @DisplayName("Customization")
  class Customization {

    @Test
    @DisplayName("names the Filigran version and no update while none is newer")
    void given_noNewerVersion_should_offerNoUpdate() throws Exception {
      // Arrange
      String base = install(FIRST);
      JsonNode copy = customize(base);
      entityManager.flush();

      // Act & Assert
      customization(copy.path("injector_contract_id").asText())
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.customization_base_action_id").value(contractOf(base).getId()))
          .andExpect(jsonPath("$.customization_base_version").value(FIRST))
          .andExpect(jsonPath("$.customization_update").doesNotExist());
    }

    @Test
    @DisplayName("lists what the newer version changes, the block both changed as one conflict")
    void given_newerVersion_should_listItsChangesAndConflicts() throws Exception {
      // Arrange
      JsonNode copy = customize(install(FIRST));
      changeCommand(copy.path("action_payload").path("payload_id").asText(), MY_COMMAND);
      String newer = installNewerWithFiligranChanges();

      // Act
      JsonNode update =
          body(customization(copy.path("injector_contract_id").asText()).andExpect(status().isOk()))
              .path("customization_update");

      // Assert
      assertThat(update.path("update_payload_id").asText()).isEqualTo(newer);
      assertThat(update.path("update_version").asText()).isEqualTo(NEWER);
      assertThat(update.path("update_fields").findValuesAsText("field_name"))
          .containsExactlyInAnyOrder("COMMAND_CONTENT", "CLEANUP_COMMAND");
      JsonNode command = fieldOf(update, "COMMAND_CONTENT");
      assertThat(command.path("field_block").asText()).isEqualTo("EXECUTION");
      assertThat(command.path("field_status").asText()).isEqualTo("CONFLICT");
      assertThat(command.path("field_filigran").asText()).isEqualTo(FILIGRAN_COMMAND);
      assertThat(command.path("field_yours").asText()).isEqualTo(MY_COMMAND);
      JsonNode cleanup = fieldOf(update, "CLEANUP_COMMAND");
      assertThat(cleanup.path("field_block").asText()).isEqualTo("EXECUTION");
      assertThat(cleanup.path("field_status").asText()).isEqualTo("CONFLICT");
      JsonNode releaseNote = update.path("update_release_notes").path(0);
      assertThat(releaseNote.path("release_version").asText()).isEqualTo(NEWER);
      assertThat(releaseNote.path("release_changelog").asText()).isEqualTo(NEWER_CHANGELOG);
    }

    @Test
    @DisplayName("refuses an action that is not a customized copy")
    void given_filigranAction_should_refuseIt() throws Exception {
      // Act & Assert
      customization(contractOf(install(FIRST)).getId())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(NOT_CUSTOMIZED));
    }

    private JsonNode fieldOf(JsonNode update, String name) {
      for (JsonNode field : update.path("update_fields")) {
        if (name.equals(field.path("field_name").asText())) {
          return field;
        }
      }
      throw new AssertionError("No field " + name);
    }
  }

  @Nested
  @DisplayName("Update")
  class Update {

    @Test
    @DisplayName("keeps my whole execution block on the newer version when I keep mine")
    void given_keepMine_should_keepMyExecutionBlock() throws Exception {
      // Arrange
      JsonNode copy = customize(install(FIRST));
      String copyId = copy.path("action_payload").path("payload_id").asText();
      String myCleanup = ((Command) Hibernate.unproxy(payload(copyId))).getCleanupCommand();
      changeCommand(copyId, MY_COMMAND);
      String newer = installNewerWithFiligranChanges();

      // Act
      JsonNode saved =
          body(
              update(
                      copy.path("injector_contract_id").asText(),
                      newer,
                      Map.of("EXECUTION", "KEEP_MINE"))
                  .andExpect(status().isOk()));

      // Assert
      Command version =
          (Command)
              Hibernate.unproxy(payload(saved.path("action_payload").path("payload_id").asText()));
      assertThat(version.getContent()).isEqualTo(MY_COMMAND);
      assertThat(version.getCleanupCommand()).isEqualTo(myCleanup);
      assertThat(version.getBase().getId()).isEqualTo(newer);
      assertThat(version.getVersion()).isEqualTo("1.0.1");
      assertThat(saved.path("action_payload_customized").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("takes Filigran's whole execution block when I choose Filigran's")
    void given_takeFiligran_should_takeFiligranExecutionBlock() throws Exception {
      // Arrange
      JsonNode copy = customize(install(FIRST));
      changeCommand(copy.path("action_payload").path("payload_id").asText(), MY_COMMAND);
      String newer = installNewerWithFiligranChanges();

      // Act
      JsonNode saved =
          body(
              update(
                      copy.path("injector_contract_id").asText(),
                      newer,
                      Map.of("EXECUTION", "TAKE_APPLIED"))
                  .andExpect(status().isOk()));

      // Assert
      Command version =
          (Command)
              Hibernate.unproxy(payload(saved.path("action_payload").path("payload_id").asText()));
      assertThat(version.getContent()).isEqualTo(FILIGRAN_COMMAND);
      assertThat(version.getCleanupCommand()).isEqualTo(FILIGRAN_CLEANUP);
    }

    @Test
    @DisplayName("takes Filigran's changes without a choice when I changed nothing")
    void given_unchangedCopy_should_takeFiligranChangesWithoutChoice() throws Exception {
      // Arrange
      JsonNode copy = customize(install(FIRST));
      entityManager.flush();
      String newer = installNewerWithFiligranChanges();

      // Act
      JsonNode saved =
          body(
              update(copy.path("injector_contract_id").asText(), newer, Map.of())
                  .andExpect(status().isOk()));

      // Assert
      Command version =
          (Command)
              Hibernate.unproxy(payload(saved.path("action_payload").path("payload_id").asText()));
      assertThat(version.getContent()).isEqualTo(FILIGRAN_COMMAND);
      assertThat(version.getCleanupCommand()).isEqualTo(FILIGRAN_CLEANUP);
    }

    @Test
    @DisplayName("refuses a conflict left without a choice")
    void given_conflictWithoutChoice_should_refuseIt() throws Exception {
      // Arrange
      JsonNode copy = customize(install(FIRST));
      changeCommand(copy.path("action_payload").path("payload_id").asText(), MY_COMMAND);
      String newer = installNewerWithFiligranChanges();

      // Act & Assert
      update(copy.path("injector_contract_id").asText(), newer, Map.of())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(CONFLICT_WITHOUT_CHOICE));
    }

    @Test
    @DisplayName("refuses a version that is not the update to take")
    void given_otherVersion_should_refuseIt() throws Exception {
      // Arrange
      String base = install(FIRST);
      JsonNode copy = customize(base);
      installNewerWithFiligranChanges();

      // Act & Assert
      update(copy.path("injector_contract_id").asText(), base, Map.of())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(UPDATE_CHANGED));
    }

    @Test
    @DisplayName("refuses an older version of the copy")
    void given_olderVersionOfTheCopy_should_refuseIt() throws Exception {
      // Arrange
      JsonNode copy = customize(install(FIRST));
      String copyId = copy.path("action_payload").path("payload_id").asText();
      String copyActionId = copy.path("injector_contract_id").asText();
      entityManager.flush();
      entityManager.clear();
      mvc.perform(
              put(TENANT_THREAT_ARSENAL_URL + "/{actionId}", DEFAULT_TENANT_UUID, copyActionId)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(edit(copyId, MY_COMMAND, ""))))
          .andExpect(status().isOk());
      String newer = installNewerWithFiligranChanges();

      // Act & Assert
      update(copyActionId, newer, Map.of())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value(NOT_LATEST));
    }
  }

  @Nested
  @DisplayName("Another tenant")
  class OtherTenant {

    @Test
    @DisplayName("neither customizes nor reads an action of another tenant")
    void given_otherTenantPath_should_neitherCustomizeNorRead() throws Exception {
      // Arrange
      String base = install(FIRST);
      String actionId = contractOf(base).getId();
      // A request leaves the test thread without a user: the tenant creation needs one.
      testUserHolder.refreshSecurityContext();
      String otherTenant = tenantHelper.createTenantWithCurrentUser("customization-other").getId();

      // Act & Assert
      mvc.perform(
              post(TENANT_THREAT_ARSENAL_URL + "/{actionId}/customize", otherTenant, actionId)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());
      mvc.perform(
              get(TENANT_THREAT_ARSENAL_URL + "/{actionId}/customization", otherTenant, actionId))
          .andExpect(status().is4xxClientError());
      assertThat(payloadRepository.findAll()).noneMatch(Payload::isCustomized);
    }
  }

  @Nested
  @DisplayName("Filigran version removed")
  class BaseRemoved {

    @Test
    @DisplayName("keeps the copy, no longer linked")
    void given_baseRemoved_should_keepTheCopy() throws Exception {
      // Arrange
      String base = install(FIRST);
      install(NEWER);
      String copyId = customize(base).path("action_payload").path("payload_id").asText();
      entityManager.flush();
      entityManager.clear();

      // Act: a catalogue version is never deleted through the application, only in SQL.
      entityManager
          .createNativeQuery("DELETE FROM payloads WHERE payload_id = :id")
          .setParameter("id", base)
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertThat(payload(copyId).isCustomized()).isFalse();
    }
  }
}
