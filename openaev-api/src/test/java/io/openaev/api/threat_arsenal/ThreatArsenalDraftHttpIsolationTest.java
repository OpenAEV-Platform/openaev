package io.openaev.api.threat_arsenal;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.PayloadDraftInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.ThreatArsenalInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code payload_drafts} activated (and every other production table:
 * the drafts flow reads payloads, payload identities, approvals and content history, all v2), the
 * tenant scope set from the URL path isolates the drafts through the real {@link
 * ThreatArsenalDraftApi} endpoints. A user who belongs to two tenants sees and changes a draft only
 * under its own tenant's path; a draft created under a path belongs to that path's tenant.
 *
 * <p>Each tenant has an action (a payload and its injector contract) and a draft, seeded natively,
 * never through the API: two API writes to two tenants in one test transaction would set the scope
 * twice. Each test stays on tenant A's path; ground truth is read in raw JDBC, which the statement
 * inspector never rewrites.
 *
 * <p>Tenant B also owns a "planted" draft that points at tenant A's payload. No API flow writes
 * such a row (a draft copies its payload's tenant); it exists to prove the {@code payload_drafts}
 * scope on its own, not merely the identity check that already separates two payloads.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("payload_drafts read and write isolation through the real HTTP endpoints")
class ThreatArsenalDraftHttpIsolationTest extends IntegrationTest {

  // The production list, as other round-trip scope tests arm it: one shared cached context.
  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_DRAFTS =
      "/api/tenants/{tenantId}/threat_arsenals/{actionId}/drafts";
  private static final String TENANT_DRAFT = TENANT_DRAFTS + "/{draftId}";
  private static final String TENANT_NEW_ACTION_DRAFTS =
      "/api/tenants/{tenantId}/threat_arsenals/new_action_drafts";
  private static final String TENANT_NEW_ACTION_DRAFT = TENANT_NEW_ACTION_DRAFTS + "/{draftId}";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private Seeded actionA;
  private Seeded actionB;
  private String draftA;
  private String draftB;
  private String plantedDraftB;
  private String newActionDraftA;
  private String newActionDraftB;

  /** A seeded action: its contract id, payload id and payload identity id. */
  private record Seeded(String actionId, String payloadId, String identityId) {}

  @BeforeEach
  void seedTwoTenantsWithOneActionAndOneDraftEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("draft-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("draft-iso-b").getId();
    actionA = seedAction(tenantA, "draft-iso-action-a-" + UUID.randomUUID());
    actionB = seedAction(tenantB, "draft-iso-action-b-" + UUID.randomUUID());
    draftA = seedDraft(tenantA, actionA, "echo draft of a");
    draftB = seedDraft(tenantB, actionB, "echo draft of b");
    plantedDraftB = seedDraft(tenantB, actionA, "echo planted by b");
    newActionDraftA = seedNewActionDraft(tenantA, "New action of a");
    newActionDraftB = seedNewActionDraft(tenantB, "New action of b");
    // Evict the seed from the session: the reads below must issue real, rewritten SELECTs.
    entityManager.clear();
  }

  @Nested
  @DisplayName("Listing drafts")
  class Listing {

    @Test
    @DisplayName("given A's action under A's path, should list A's draft and never B's")
    void given_ownActionUnderOwnPath_should_listOnlyOwnDrafts() throws Exception {
      // Act
      String response =
          mvc.perform(get(TENANT_DRAFTS, tenantA, actionA.actionId()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].draft_id");
      assertThat(ids).containsExactly(draftA);
      assertThat(response).doesNotContain(draftB, plantedDraftB, "echo planted by b");
      // Ground truth: B's draft on A's payload exists, the scope hid it.
      assertThat(rawCount(plantedDraftB)).isEqualTo(1L);
    }

    @Test
    @DisplayName("given B's action under A's path, should be not found")
    void given_otherTenantActionUnderOwnPath_should_beNotFound() throws Exception {
      // Act
      String response =
          mvc.perform(get(TENANT_DRAFTS, tenantA, actionB.actionId()))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(response).doesNotContain(draftB, "echo draft of b");
    }
  }

  @Nested
  @DisplayName("Saving a draft")
  class Saving {

    @Test
    @DisplayName("given A's draft under A's path, should save it")
    void given_ownDraftUnderOwnPath_should_save() throws Exception {
      // Act
      mvc.perform(
              put(TENANT_DRAFT, tenantA, actionA.actionId(), draftA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(draftContent("echo saved by a")))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertThat(rawContent(draftA)).contains("echo saved by a");
    }

    @Test
    @DisplayName("given B's draft and B's action under A's path, should be not found and unchanged")
    void given_otherTenantDraftByItsActionUnderOwnPath_should_beNotFound() throws Exception {
      // Act
      mvc.perform(
              put(TENANT_DRAFT, tenantA, actionB.actionId(), draftB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(draftContent("echo hijack")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawContent(draftB)).contains("echo draft of b").doesNotContain("hijack");
    }

    @Test
    @DisplayName(
        "given B's draft on A's payload under A's path, should be not found and unchanged (the"
            + " payload_drafts scope alone hides it)")
    void given_otherTenantDraftOnOwnActionUnderOwnPath_should_beNotFound() throws Exception {
      // Act
      mvc.perform(
              put(TENANT_DRAFT, tenantA, actionA.actionId(), plantedDraftB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(draftContent("echo hijack")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawContent(plantedDraftB)).contains("echo planted by b").doesNotContain("hijack");
    }
  }

  @Nested
  @DisplayName("Discarding a draft")
  class Discarding {

    @Test
    @DisplayName("given A's draft under A's path, should discard it")
    void given_ownDraftUnderOwnPath_should_discard() throws Exception {
      // Act
      mvc.perform(delete(TENANT_DRAFT, tenantA, actionA.actionId(), draftA).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(rawCount(draftA)).isZero();
    }

    @Test
    @DisplayName(
        "given B's draft and B's action under A's path, should be refused and B's draft survives")
    void given_otherTenantDraftByItsActionUnderOwnPath_should_keepRow() throws Exception {
      // Act
      // The handler looks the action and the draft up inside the scope before deleting: the
      // lookup finds nothing, so the refusal is a 404, not the 2xx no-op of a bare DELETE.
      mvc.perform(delete(TENANT_DRAFT, tenantA, actionB.actionId(), draftB).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawCount(draftB)).isEqualTo(1L);
    }

    @Test
    @DisplayName("given B's draft on A's payload under A's path, should be refused and survive")
    void given_otherTenantDraftOnOwnActionUnderOwnPath_should_keepRow() throws Exception {
      // Act
      mvc.perform(delete(TENANT_DRAFT, tenantA, actionA.actionId(), plantedDraftB).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawCount(plantedDraftB)).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("Publishing a draft")
  class Publishing {

    @Test
    @DisplayName(
        "given B's draft and B's action under A's path, should be refused, no version added to B")
    void given_otherTenantDraftByItsActionUnderOwnPath_should_refuse() throws Exception {
      // Act
      mvc.perform(
              post(TENANT_DRAFT + "/publish", tenantA, actionB.actionId(), draftB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(publishInput("echo published by a")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawVersionCount(actionB.identityId())).isEqualTo(1L);
      assertThat(rawCount(draftB)).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "given B's draft on A's payload under A's path, should be refused, no version added")
    void given_otherTenantDraftOnOwnActionUnderOwnPath_should_refuse() throws Exception {
      // Act
      mvc.perform(
              post(TENANT_DRAFT + "/publish", tenantA, actionA.actionId(), plantedDraftB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(publishInput("echo published by a")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawVersionCount(actionA.identityId())).isEqualTo(1L);
      assertThat(rawCount(plantedDraftB)).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("Creating a draft")
  class Creating {

    @Test
    @DisplayName("given A's action under A's path, should attribute the new draft to tenant A")
    void given_ownActionUnderOwnPath_should_attributeToPathTenant() throws Exception {
      // Act
      String response =
          mvc.perform(
                  post(TENANT_DRAFTS, tenantA, actionA.actionId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(draftContent("echo new draft of a")))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String created = JsonPath.read(response, "$.draft_id");
      assertThat(rawTenant(created)).isEqualTo(tenantA);
    }

    @Test
    @DisplayName("given B's action under A's path, should be not found and create nothing")
    void given_otherTenantActionUnderOwnPath_should_beNotFound() throws Exception {
      // Act
      mvc.perform(
              post(TENANT_DRAFTS, tenantA, actionB.actionId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(draftContent("echo injected by a")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawDraftCount(actionB.identityId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("given no tenant selector, should refuse the create (a single tenant is required)")
    void given_noTenantSelector_should_beBadRequest() throws Exception {
      // Act / Assert
      mvc.perform(
              post(ThreatArsenalApi.THREAT_ARSENAL_URL + "/{actionId}/drafts", actionA.actionId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(draftContent("echo no selector")))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
      assertThat(rawDraftCount(actionA.identityId())).isEqualTo(2L);
    }
  }

  @Nested
  @DisplayName("Drafts of new actions")
  class NewActionDrafts {

    @Test
    @DisplayName("given A's path, should list A's new action draft and never B's")
    void given_ownPath_should_listOnlyOwnNewActionDrafts() throws Exception {
      // Act
      String response =
          mvc.perform(get(TENANT_NEW_ACTION_DRAFTS, tenantA))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat((List<String>) JsonPath.read(response, "$[*].draft_id"))
          .contains(newActionDraftA)
          .doesNotContain(newActionDraftB);
      assertThat(response).doesNotContain("New action of b");
    }

    @Test
    @DisplayName("given B's new action draft under A's path, should not save it")
    void given_otherTenantNewActionDraft_should_notSave() throws Exception {
      // Act
      mvc.perform(
              put(TENANT_NEW_ACTION_DRAFT, tenantA, newActionDraftB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(draftContent("echo hijack")))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawContent(newActionDraftB)).contains("New action of b").doesNotContain("hijack");
    }

    @Test
    @DisplayName("given B's new action draft under A's path, should not discard it")
    void given_otherTenantNewActionDraft_should_keepRow() throws Exception {
      // Act
      mvc.perform(delete(TENANT_NEW_ACTION_DRAFT, tenantA, newActionDraftB).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawCount(newActionDraftB)).isEqualTo(1L);
    }

    @Test
    @DisplayName("given B's new action draft under A's path, should not publish it")
    void given_otherTenantNewActionDraft_should_notPublish() throws Exception {
      // Act
      mvc.perform(
              post(TENANT_NEW_ACTION_DRAFT + "/publish", tenantA, newActionDraftB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(createInput("Published by a " + UUID.randomUUID())))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertThat(rawCount(newActionDraftB)).isEqualTo(1L);
    }

    @Test
    @DisplayName("given A's path, should attribute a new action draft to tenant A")
    void given_ownPath_should_attributeNewActionDraftToPathTenant() throws Exception {
      // Act
      String response =
          mvc.perform(
                  post(TENANT_NEW_ACTION_DRAFTS, tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(draftContent("echo new action of a")))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String created = JsonPath.read(response, "$.draft_id");
      assertThat(rawTenant(created)).isEqualTo(tenantA);
    }
  }

  // -- Inputs --

  private static ThreatArsenalActionCreateInput createInput(String name) {
    return ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(), name);
  }

  private PayloadDraftInput draftContent(String command) {
    return new PayloadDraftInput(mapper.createObjectNode().put("action_content", command));
  }

  private static ThreatArsenalActionUpdateInput publishInput(String content) {
    return new ThreatArsenalActionUpdateInput(
        "Published from a draft",
        new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
        null,
        "bash",
        content,
        Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
        new BaseInjectExpectation.EXPECTATION_TYPE[] {},
        Collections.emptyMap(),
        null,
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        null,
        null,
        List.of());
  }

  // -- Native seed: no scope is set, both tenants' rows land in the same transaction --

  private Seeded seedAction(String tenantId, String name) {
    Command command = (Command) PayloadFixture.createCommand("bash", "echo hello", List.of(), null);
    command.setName(name);
    command.setTenant(new Tenant(tenantId));
    entityManager.persist(command);
    entityManager.flush();
    String contractId = UUID.randomUUID().toString();
    jdbcUpdate(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " injector_contract_payload, tenant_id) VALUES (?, '{}', ?, ?)",
        contractId,
        command.getId(),
        tenantId);
    return new Seeded(contractId, command.getId(), command.getIdentity().getId());
  }

  private String seedDraft(String tenantId, Seeded action, String command) {
    String draftId = UUID.randomUUID().toString();
    jdbcUpdate(
        "INSERT INTO payload_drafts (payload_draft_id, tenant_id, payload_identity_id,"
            + " payload_draft_source, payload_draft_source_version, payload_draft_content)"
            + " VALUES (?, ?, ?, ?, '1.0.0', ?)",
        draftId,
        tenantId,
        action.identityId(),
        action.payloadId(),
        "{\"action_content\":\"" + command + "\"}");
    return draftId;
  }

  // A draft of a new action: no payload yet, saved by the current user (so "my drafts" sees it).
  private String seedNewActionDraft(String tenantId, String name) {
    String draftId = UUID.randomUUID().toString();
    jdbcUpdate(
        "INSERT INTO payload_drafts (payload_draft_id, tenant_id, payload_draft_content,"
            + " payload_draft_created_by) VALUES (?, ?, ?, ?)",
        draftId,
        tenantId,
        "{\"action_name\":\"" + name + "\"}",
        testUserHolder.get().getId());
    return draftId;
  }

  private void jdbcUpdate(String sql, String... parameters) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                statement.executeUpdate();
              }
            });
  }

  // -- Ground truth: raw JDBC on the test's own connection, never rewritten by the inspector. A
  // flush first, so a pending scoped write reaches the database. --

  private String rawContent(String draftId) {
    return rawString(
        "SELECT payload_draft_content FROM payload_drafts WHERE payload_draft_id = ?", draftId);
  }

  private String rawTenant(String draftId) {
    return rawString("SELECT tenant_id FROM payload_drafts WHERE payload_draft_id = ?", draftId);
  }

  private long rawCount(String draftId) {
    return rawLong("SELECT count(*) FROM payload_drafts WHERE payload_draft_id = ?", draftId);
  }

  private long rawDraftCount(String identityId) {
    return rawLong("SELECT count(*) FROM payload_drafts WHERE payload_identity_id = ?", identityId);
  }

  private long rawVersionCount(String identityId) {
    return rawLong("SELECT count(*) FROM payloads WHERE identity_id = ?", identityId);
  }

  private String rawString(String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawLong(String sql, String parameter) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
