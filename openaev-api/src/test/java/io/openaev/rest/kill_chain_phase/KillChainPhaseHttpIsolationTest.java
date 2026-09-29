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
 *
 * <p>Each test stays on a single tenant path so the per-request scope is set once: re-applying the
 * same scope inside the test transaction is tolerated, changing it would hit the nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=kill_chain_phases")
@WithMockUser(isAdmin = true)
@DisplayName("kill_chain_phases read and write isolation through the real HTTP endpoint")
class KillChainPhaseHttpIsolationTest extends IntegrationTest {

  private static final String PHASES = "/api/kill_chain_phases";
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
    phaseA = seedPhase(tenantA, "phase-a", "shortname-a", "TA9901");
    phaseB = seedPhase(tenantB, "phase-b", "shortname-b", "TA9902");
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
  @DisplayName("under tenant A's path: the list returns A's phase and not B's")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(TENANT_PHASES, tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(phaseA), "A's phase must appear in A's list");
    assertFalse(response.contains(phaseB), "B's phase must not appear in A's list");
  }

  @Test
  @DisplayName(
      "via the X-Tenant-Ids header (no path tenant): the list returns A's phase and not B's")
  void listViaHeaderReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(PHASES).header("X-Tenant-Ids", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(phaseA), "A's phase must appear when A is selected via header");
    assertFalse(response.contains(phaseB), "B's phase must not appear");
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
  @DisplayName("under tenant A's path: the name options only expose A's phase")
  void optionsUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(TENANT_PHASES + "/options", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(phaseA), "A's phase must appear in A's options");
    assertFalse(response.contains(phaseB), "B's phase must not appear in A's options");
  }

  @Test
  @DisplayName("under tenant A's path: resolving B's id by options returns nothing")
  void optionsByIdUnderTenantACannotResolveB() throws Exception {
    String response =
        mvc.perform(
                post(TENANT_PHASES + "/options", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[\"" + phaseB + "\"]")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(response.contains(phaseB), "B's phase must not be resolvable under A's path");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    String response =
        mvc.perform(
                post(TENANT_PHASES, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createInput("created-under-a", "created-under-a", "TA9910"))
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
    mvc.perform(
            post(PHASES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createInput("no-selector", "no-selector", "TA9911"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own phase")
  void updateUnderTenantAUpdatesOwnPhase() throws Exception {
    mvc.perform(
            put(TENANT_PHASE_BY_ID, tenantA, phaseA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateInput("renamed-a"))
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals("renamed-a", rawName(phaseA), "A's own phase must be updated");
  }

  @Test
  @DisplayName("under tenant A's path: updating B's phase is not found and leaves it untouched")
  void updateUnderTenantAOfBPhaseIsBlocked() throws Exception {
    mvc.perform(
            put(TENANT_PHASE_BY_ID, tenantA, phaseB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateInput("hijacked"))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("phase-b", rawName(phaseB), "B's phase must be untouched");
  }

  @Test
  @DisplayName("under tenant A's path: A can delete its own phase")
  void deleteUnderTenantADeletesOwnPhase() throws Exception {
    mvc.perform(delete(TENANT_PHASE_BY_ID, tenantA, phaseA).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(0L, rawCount(phaseA), "A's own phase must be deleted");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's phase is a no-op and leaves it in place")
  void deleteUnderTenantAOfBPhaseIsBlocked() throws Exception {
    mvc.perform(delete(TENANT_PHASE_BY_ID, tenantA, phaseB).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(1L, rawCount(phaseB), "B's phase must survive tenant A's delete attempt");
  }

  private static String createInput(String name, String shortName, String externalId) {
    return "{\"phase_kill_chain_name\":\"mitre-attack\","
        + "\"phase_name\":\""
        + name
        + "\","
        + "\"phase_shortname\":\""
        + shortName
        + "\","
        + "\"phase_external_id\":\""
        + externalId
        + "\","
        + "\"phase_order\":1}";
  }

  private static String updateInput(String name) {
    return "{\"phase_kill_chain_name\":\"mitre-attack\",\"phase_name\":\""
        + name
        + "\",\"phase_order\":1}";
  }

  // Ground-truth reads, bypassing the scope: raw JDBC on the test's own connection sees the
  // uncommitted seed and the rewriter does not touch a statement it never generated. A flush first
  // forces any pending scoped UPDATE/DELETE to reach the database.
  private String rawName(String phaseId) {
    return rawQuery(
        "SELECT phase_name FROM kill_chain_phases WHERE phase_id = ?",
        statement -> statement.setString(1, phaseId),
        rows -> rows.next() ? rows.getString(1) : null);
  }

  private long rawCount(String phaseId) {
    return rawQuery(
        "SELECT count(*) FROM kill_chain_phases WHERE phase_id = ?",
        statement -> statement.setString(1, phaseId),
        rows -> {
          rows.next();
          return rows.getLong(1);
        });
  }

  private <T> T rawQuery(String sql, StatementBinder binder, ResultReader<T> reader) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet rows = statement.executeQuery()) {
                  return reader.read(rows);
                }
              }
            });
  }

  private interface StatementBinder {
    void bind(PreparedStatement statement) throws java.sql.SQLException;
  }

  private interface ResultReader<T> {
    T read(ResultSet rows) throws java.sql.SQLException;
  }

  private String seedPhase(String tenantId, String name, String shortName, String externalId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO kill_chain_phases"
                + " (phase_id, phase_name, phase_shortname, phase_kill_chain_name,"
                + "  phase_external_id, phase_stix_id, phase_order, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, shortName)
        .setParameter(4, "mitre-attack")
        .setParameter(5, externalId)
        .setParameter(6, "x-mitre-tactic--" + UUID.randomUUID())
        .setParameter(7, 1L)
        .setParameter(8, tenantId)
        .executeUpdate();
    return id;
  }
}
