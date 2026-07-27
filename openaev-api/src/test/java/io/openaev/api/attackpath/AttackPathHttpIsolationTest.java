package io.openaev.api.attackpath;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
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
      "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH",
      "openaev.tenant.active-tables=attackpath_execution,attackpath_finding"
    })
@WithMockUser(isAdmin = true)
@DisplayName("attack path POC read isolation through the real HTTP endpoints")
class AttackPathHttpIsolationTest extends IntegrationTest {

  private static final String SIM = "SIM-ISO";
  private static final String ENDPOINT_KEY = "dc-01";
  // Both mappings the API declares, derived from its own constants rather than retyped: a route
  // renamed on the controller must break this test, not silently stop covering it.
  private static final String SCOPED = TENANT_PREFIX + "/attack-path";
  private static final String PLAIN = AttackPathApi.ATTACK_PATH_URI;

  private static final String GRAPH = SCOPED + "/simulations/{simulationId}/graph";
  private static final String DELTA = SCOPED + "/simulations/{simulationId}/graph/delta";
  private static final String EXPAND = SCOPED + "/simulations/{simulationId}/endpoint/findings";
  private static final String RELATIONS = SCOPED + "/simulations/{simulationId}/endpoint/relations";
  private static final String LIST = SCOPED + "/simulations";
  private static final String FINDINGS = SCOPED + "/simulations/{simulationId}/findings";
  private static final String EXECUTION = SCOPED + "/simulations/{simulationId}/execution";
  // The same handlers without the tenant prefix: here the scope comes from the header.
  private static final String PLAIN_GRAPH = PLAIN + "/simulations/{simulationId}/graph";
  private static final String PLAIN_LIST = PLAIN + "/simulations";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  private String tenantA;
  private String tenantB;
  private String executionId;
  private String findingId;

  @BeforeEach
  void seedGraphUnderTenantA() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("ap-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("ap-iso-b").getId();
    executionId = seedExecution(tenantA);
    findingId = seedFinding(tenantA);
    linkExecutionFinding(executionId, findingId);
    seedGraphVersion(tenantA);
  }

  @Test
  @DisplayName("under the owner tenant's path: the delta from version 0 carries the graph")
  void deltaUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(DELTA, tenantA, SIM).param("since", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resyncRequired").value(false))
        .andExpect(jsonPath("$.attackPathNodes").isNotEmpty())
        .andExpect(jsonPath("$.attackPathEdges").isNotEmpty());
  }

  @Test
  @DisplayName("under another tenant's path: the delta carries none of the owner's rows")
  void deltaUnderOtherTenantIsHidden() throws Exception {
    // The delta is a second, polled path into the same projection, so it needs its own proof: the
    // cursor reads and the affected-endpoint aggregations are all separate queries from the
    // snapshot's. The version number itself is not tenant data (it is a counter behind
    // assertCanReadSimulation); what must never cross is a row, a node, an edge or a count.
    mvc.perform(get(DELTA, tenantB, SIM).param("since", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attackPathNodes").isEmpty())
        .andExpect(jsonPath("$.attackPathEdges").isEmpty())
        .andExpect(jsonPath("$.attackPathExecutions").isEmpty())
        .andExpect(jsonPath("$.staticAttackPathFindings").isEmpty())
        .andExpect(jsonPath("$.counters.endpoints").value(0))
        .andExpect(jsonPath("$.counters.credentials").value(0));
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
  @DisplayName("under the owner tenant's path: the findings list returns the category's findings")
  void findingsUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(FINDINGS, tenantA, SIM).param("category", "credentials"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items").isNotEmpty());
  }

  @Test
  @DisplayName("under another tenant's path: the findings list is empty (no leak)")
  void findingsUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(FINDINGS, tenantB, SIM).param("category", "credentials"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  @DisplayName("under the owner tenant's path: the execution detail is visible")
  void executionDetailUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(EXECUTION, tenantA, SIM).param("ref", executionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.endpointKey").value(ENDPOINT_KEY));
  }

  @Test
  @DisplayName("under another tenant's path: the execution detail is not found (no leak)")
  void executionDetailUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(EXECUTION, tenantB, SIM).param("ref", executionId))
        .andExpect(status().isNotFound());
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
  @DisplayName("via the X-Tenant-Ids header (no path tenant): the owner's graph is visible")
  void graphViaHeaderForOwnerTenantIsVisible() throws Exception {
    // Second scope-carrying route: the same handlers are also mapped without the tenant prefix, and
    // the scope then comes from the header instead of the path. Different plumbing, so path
    // coverage does not imply header coverage.
    mvc.perform(get(PLAIN_GRAPH, SIM).header("X-Tenant-Ids", tenantA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.counters.endpoints").value(1))
        .andExpect(jsonPath("$.attackPathNodes").isNotEmpty());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: another tenant selected, the graph is empty (no leak)")
  void graphViaHeaderForOtherTenantIsHidden() throws Exception {
    mvc.perform(get(PLAIN_GRAPH, SIM).header("X-Tenant-Ids", tenantB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attackPathNodes").isEmpty())
        .andExpect(jsonPath("$.attackPathEdges").isEmpty());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: the picker lists the selected owner's simulation")
  void simulationsListViaHeaderForOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(PLAIN_LIST).header("X-Tenant-Ids", tenantA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.simulationId=='" + SIM + "')]").exists());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: the picker hides another tenant's simulation")
  void simulationsListViaHeaderForOtherTenantIsHidden() throws Exception {
    // Deliberately a separate method: the aspect refuses to redefine the scope inside one
    // transaction, so the two selections cannot share a test.
    mvc.perform(get(PLAIN_LIST).header("X-Tenant-Ids", tenantB))
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

  @Test
  @DisplayName("no scope set: the finding-to-execution links are fail-closed like every other read")
  void linkReadWithoutScopeIsFailClosed() {
    // The links live in attackpath_execution_finding, a child table with no tenant_id of its own,
    // so
    // it is deliberately not tenant-active. That is fine only as long as it is never read on its
    // own: reached through its guarded parent, it inherits the parent's scope. This pins that
    // property, so a future query that drops the parent join fails here instead of leaking quietly.
    assertThat(findingRepository.findExecutionLinks(List.of(findingId))).isEmpty();
    assertThat(rawLinkCount(findingId)).isEqualTo(1L);
  }

  // Native seed, not the API: the setup seeds two tenants, and an explicit tenant_id lets both rows
  // land without a request scope while keeping the read cases independent of any write endpoint.
  private String seedExecution(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
                + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
                + " attackpath_execution_source_injector, attackpath_execution_target_kind,"
                + " attackpath_execution_target_asset_id, attackpath_execution_target_key,"
                + " attackpath_execution_target_hostname, attackpath_execution_executed_at,"
                + " attackpath_execution_prevention_status, attackpath_execution_row_version)"
                + " VALUES (:id, :tenant, :sim, 'INJECTOR',"
                + " 'NMAP', 'ASSET', :ep, :ep, 'CORP-DC-01', TIMESTAMP '2026-06-18 08:00:00',"
                + " 'Prevented', 1)")
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
            "INSERT INTO attackpath_finding (attackpath_finding_id, tenant_id,"
                + " attackpath_finding_simulation_id, attackpath_finding_type,"
                + " attackpath_finding_value, attackpath_finding_endpoint_id,"
                + " attackpath_finding_endpoint_key, attackpath_finding_row_version)"
                + " VALUES (:id, :tenant, :sim, 'credentials', 'admin:secret', :ep, :ep, 1)")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("sim", SIM)
        .setParameter("ep", ENDPOINT_KEY)
        .executeUpdate();
    return id;
  }

  /**
   * The simulation's version counter, as a writer would have left it after stamping the seeded rows
   * at version 1. Without it the delta read has nothing to compare a cursor against.
   */
  private void seedGraphVersion(String tenantId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_graph_version (attackpath_graph_version_simulation_id,"
                + " tenant_id, attackpath_graph_version_value) VALUES (:sim, :tenant, 1)")
        .setParameter("sim", SIM)
        .setParameter("tenant", tenantId)
        .executeUpdate();
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
                      "SELECT count(*) FROM attackpath_execution WHERE attackpath_execution_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private long rawLinkCount(String finding) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM attackpath_execution_finding WHERE finding_id = ?")) {
                statement.setString(1, finding);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
