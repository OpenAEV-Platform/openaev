package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with the {@code attackpath_*} tables activated, the tenant scope set from
 * the request path isolates the POC read endpoints: the owner tenant sees its simulation's graph, a
 * different tenant sees an empty one (never the owner's data), and a scope-less read is
 * fail-closed. This exercises the whole chain (the {@code TxCtx} binding, the transaction aspect,
 * the {@code can_access_tenant} rewrite, and the JPQL reads), so it proves the isolation claim
 * rather than the mere presence of a guard. It is the HTTP-layer complement to the inspector's own
 * SQL-layer tests.
 *
 * <p>Each test stays on a single tenant path: the per-request scope is set once and the aspect
 * refuses to redefine it within one transaction.
 */
@Transactional
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH_POC",
      "openaev.tenant.active-tables=attackpath_execution,attackpath_finding"
    })
@WithMockUser(isAdmin = true)
@DisplayName("attack path POC read isolation through the real HTTP endpoints")
class AttackPathPocHttpIsolationTest extends IntegrationTest {

  private static final String SIM = "SIM-ISO";
  private static final String ENDPOINT_KEY = "dc-01";
  private static final String GRAPH =
      "/api/tenants/{tenantId}/poc/attack-path/simulations/{simulationId}/graph";
  private static final String EXPAND =
      "/api/tenants/{tenantId}/poc/attack-path/simulations/{simulationId}/endpoint/findings";
  private static final String RELATIONS =
      "/api/tenants/{tenantId}/poc/attack-path/simulations/{simulationId}/endpoint/relations";
  private static final String LIST = "/api/tenants/{tenantId}/poc/attack-path/simulations";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private AttackPathExecutionRepository executionRepository;

  private String tenantA;
  private String tenantB;
  private String executionId;

  @BeforeEach
  void seedGraphUnderTenantA() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("ap-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("ap-iso-b").getId();
    executionId = seedExecution(tenantA);
    String findingId = seedFinding(tenantA);
    linkExecutionFinding(executionId, findingId);
  }

  @Test
  @DisplayName("under the owner tenant's path: the graph is visible")
  void graphUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(GRAPH, tenantA, SIM))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.endpoints").value(1))
        .andExpect(jsonPath("$.attackPathNodes").isNotEmpty())
        .andExpect(jsonPath("$.attackPathEdges").isNotEmpty());
  }

  @Test
  @DisplayName("under another tenant's path: the same simulation's graph is empty")
  void graphUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(GRAPH, tenantB, SIM))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attackPathNodes").isEmpty())
        .andExpect(jsonPath("$.attackPathEdges").isEmpty());
  }

  @Test
  @DisplayName("collapsed mode under the owner tenant's path: the aggregated graph is visible")
  void collapsedGraphUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(GRAPH, tenantA, SIM).param("mode", "collapsed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("collapsed"))
        .andExpect(jsonPath("$.counters.endpoints").value(1))
        .andExpect(jsonPath("$.attackPathNodes").isNotEmpty());
  }

  @Test
  @DisplayName("collapsed mode under another tenant's path: the aggregations are empty (no leak)")
  void collapsedGraphUnderOtherTenantIsHidden() throws Exception {
    // The collapsed reads are separate GROUP BY queries; prove the inspector filters them too, so
    // the aggregated view cannot leak another tenant's counts or endpoint groups.
    mvc.perform(get(GRAPH, tenantB, SIM).param("mode", "collapsed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.endpoints").value(0))
        .andExpect(jsonPath("$.attackPathNodes").isEmpty())
        .andExpect(jsonPath("$.attackPathEdges").isEmpty());
  }

  @Test
  @DisplayName("under the owner tenant's path: expanding the endpoint returns its findings")
  void expandUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(EXPAND, tenantA, SIM).param("ref", ENDPOINT_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.findings").isNotEmpty());
  }

  @Test
  @DisplayName("under another tenant's path: expanding the endpoint returns nothing")
  void expandUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(EXPAND, tenantB, SIM).param("ref", ENDPOINT_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.findings").isEmpty());
  }

  @Test
  @DisplayName("under the owner tenant's path: the endpoint's relations are visible")
  void relationsUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(RELATIONS, tenantA, SIM).param("ref", ENDPOINT_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executions").isNotEmpty())
        .andExpect(jsonPath("$.edges").isNotEmpty());
  }

  @Test
  @DisplayName("under another tenant's path: the endpoint's relations are empty")
  void relationsUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(RELATIONS, tenantB, SIM).param("ref", ENDPOINT_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executions").isEmpty())
        .andExpect(jsonPath("$.edges").isEmpty());
  }

  @Test
  @DisplayName("the simulations picker lists the owner tenant's simulation")
  void simulationsListUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(LIST, tenantA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.simulationId=='" + SIM + "')]").exists());
  }

  @Test
  @DisplayName("the simulations picker under another tenant does not list the owner's simulation")
  void simulationsListUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(LIST, tenantB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.simulationId=='" + SIM + "')]").doesNotExist());
  }

  @Test
  @DisplayName("no scope set: the read is empty although the rows exist (fail-closed)")
  void readWithoutScopeIsFailClosed() {
    // No TxCtx in this test transaction, so the aspect never set app.current_tenants and the
    // inspector denies every row. The row exists in the table, it is only hidden.
    assertThat(executionRepository.findGraphRows(SIM)).isEmpty();
    assertThat(rawExecutionCount(executionId)).isEqualTo(1L);
  }

  // Native seed, not the API: the setup seeds two tenants, and an explicit tenant_id lets both rows
  // land without a request scope while keeping the read cases independent of any write endpoint.
  private String seedExecution(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_execution (id, tenant_id, simulation_id, source_kind,"
                + " source_injector, target_kind, target_asset_id, target_key, target_hostname,"
                + " executed_at, prevention_status) VALUES (:id, :tenant, :sim, 'INJECTOR', 'NMAP',"
                + " 'ASSET', :ep, :ep, 'CORP-DC-01', TIMESTAMP '2026-06-18 08:00:00', 'Prevented')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("sim", SIM)
        .setParameter("ep", ENDPOINT_KEY)
        .executeUpdate();
    return id;
  }

  private String seedFinding(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_finding (id, tenant_id, simulation_id, type, value, endpoint_id,"
                + " endpoint_key) VALUES (:id, :tenant, :sim, 'credentials', 'admin:secret', :ep,"
                + " :ep)")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("sim", SIM)
        .setParameter("ep", ENDPOINT_KEY)
        .executeUpdate();
    return id;
  }

  private void linkExecutionFinding(String executionId, String findingId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_execution_finding (execution_id, finding_id)"
                + " VALUES (:execution, :finding)")
        .setParameter("execution", executionId)
        .setParameter("finding", findingId)
        .executeUpdate();
  }

  // Ground truth, bypassing the scope: raw JDBC on the test's own connection sees the uncommitted
  // seed and the inspector does not rewrite a statement it never generated. Flush first so any
  // pending scoped write reaches the database.
  private long rawExecutionCount(String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM attackpath_execution WHERE id = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
