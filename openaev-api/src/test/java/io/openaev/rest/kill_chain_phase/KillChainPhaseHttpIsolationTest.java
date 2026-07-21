package io.openaev.rest.kill_chain_phase;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code kill_chain_phases} activated, the tenant scope set from the
 * URL path isolates the table through the real {@link KillChainPhaseApi} endpoints. A user who
 * belongs to two tenants sees a phase only under its own tenant's path, never another tenant's.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=kill_chain_phases")
@WithMockUser(isAdmin = true)
@DisplayName("kill_chain_phases read and write isolation through the real HTTP endpoint")
class KillChainPhaseHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_PHASES = "/api/tenants/{tenantId}/kill_chain_phases";
  private static final String TENANT_PHASE_BY_ID = TENANT_PHASES + "/{phaseId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String phaseA;
  private String phaseB;

  @BeforeEach
  void seedTwoTenantsWithOnePhaseEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("kcp-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("kcp-iso-b").getId();
    phaseA = seedPhase(tenantA, "phase-a", "phase-a-short");
    phaseB = seedPhase(tenantB, "phase-b", "phase-b-short");
  }

  @Test
  @DisplayName("under tenant A's path: A's phase is visible, B's is hidden")
  void underTenantAPath() throws Exception {
    mvc.perform(get(TENANT_PHASE_BY_ID, tenantA, phaseA)).andExpect(status().isOk());
    mvc.perform(get(TENANT_PHASE_BY_ID, tenantA, phaseB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's phase is visible, A's is hidden")
  void underTenantBPath() throws Exception {
    mvc.perform(get(TENANT_PHASE_BY_ID, tenantB, phaseB)).andExpect(status().isOk());
    mvc.perform(get(TENANT_PHASE_BY_ID, tenantB, phaseA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's phase and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post(TENANT_PHASES + "/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(phaseA), "A's phase must appear in A's search results");
    assertFalse(response.contains(phaseB), "B's phase must not appear in A's search results");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    String input = createPhaseJson("created-under-a", "created-short-a");
    String response =
        mvc.perform(
                post(TENANT_PHASES, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(input)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdId = JsonPath.read(response, "$.phase_id");
    String storedTenant =
        (String)
            entityManager
                .createNativeQuery("SELECT tenant_id FROM kill_chain_phases WHERE phase_id = ?1")
                .setParameter(1, createdId)
                .getSingleResult();
    assertEquals(tenantA, storedTenant, "the created phase must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    String input = createPhaseJson("no-selector", "no-selector-short");
    mvc.perform(
            post("/api/kill_chain_phases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(input)
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("upsert under tenant A's path: new phases are attributed to tenant A")
  void upsertUnderTenantAIsAttributedToA() throws Exception {
    String upsertBody =
        "{\"kill_chain_phases\":[{\"phase_kill_chain_name\":\"mitre-attack\","
            + "\"phase_name\":\"upserted-a\","
            + "\"phase_shortname\":\"upserted-short-a\","
            + "\"phase_external_id\":\"ext-upsert-"
            + UUID.randomUUID()
            + "\"}]}";
    mvc.perform(
            post(TENANT_PHASES + "/upsert", tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertBody)
                .with(csrf()))
        .andExpect(status().isOk());

    entityManager.flush();
    String storedTenant =
        entityManager
            .unwrap(Session.class)
            .doReturningWork(
                connection -> {
                  try (PreparedStatement stmt =
                      connection.prepareStatement(
                          "SELECT tenant_id FROM kill_chain_phases"
                              + " WHERE phase_name = 'upserted-a'")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                      return rs.next() ? rs.getString(1) : null;
                    }
                  }
                });
    assertEquals(tenantA, storedTenant, "the upserted phase must belong to tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own phase")
  void updateUnderTenantAUpdatesOwnPhase() throws Exception {
    String updateInput =
        "{\"phase_kill_chain_name\":\"mitre-attack\",\"phase_name\":\"renamed-a\"}";
    mvc.perform(
            put(TENANT_PHASE_BY_ID, tenantA, phaseA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateInput)
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals("renamed-a", rawName(phaseA), "A's own phase must be updated");
  }

  @Test
  @DisplayName("under tenant A's path: updating B's phase is not found and leaves it untouched")
  void updateUnderTenantAOfBPhaseIsBlocked() throws Exception {
    String updateInput = "{\"phase_kill_chain_name\":\"mitre-attack\",\"phase_name\":\"hijacked\"}";
    mvc.perform(
            put(TENANT_PHASE_BY_ID, tenantA, phaseB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateInput)
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("phase-b", rawName(phaseB), "B's phase must be untouched");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's phase is a no-op and leaves it in place")
  void deleteUnderTenantAOfBPhaseIsBlocked() throws Exception {
    mvc.perform(delete(TENANT_PHASE_BY_ID, tenantA, phaseB).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(1L, rawCount(phaseB), "B's phase must survive tenant A's delete attempt");
  }

  // -- helpers --

  private String rawName(String phaseId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement stmt =
                  connection.prepareStatement(
                      "SELECT phase_name FROM kill_chain_phases WHERE phase_id = ?")) {
                stmt.setString(1, phaseId);
                try (ResultSet rs = stmt.executeQuery()) {
                  return rs.next() ? rs.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String phaseId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement stmt =
                  connection.prepareStatement(
                      "SELECT count(*) FROM kill_chain_phases WHERE phase_id = ?")) {
                stmt.setString(1, phaseId);
                try (ResultSet rs = stmt.executeQuery()) {
                  rs.next();
                  return rs.getLong(1);
                }
              }
            });
  }

  private String seedPhase(String tenantId, String name, String shortName) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO kill_chain_phases"
                + " (phase_id, phase_name, phase_shortname, phase_kill_chain_name,"
                + "  phase_external_id, phase_order, phase_created_at, phase_updated_at, tenant_id)"
                + " VALUES (?1, ?2, ?3, 'mitre-attack', ?4, 0, now(), now(), ?5)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, shortName)
        .setParameter(4, "ext-" + UUID.randomUUID())
        .setParameter(5, tenantId)
        .executeUpdate();
    return id;
  }

  private String createPhaseJson(String name, String shortName) {
    return "{\"phase_kill_chain_name\":\"mitre-attack\","
        + "\"phase_name\":\""
        + name
        + "\","
        + "\"phase_shortname\":\""
        + shortName
        + "\","
        + "\"phase_external_id\":\"ext-"
        + UUID.randomUUID()
        + "\"}";
  }
}
