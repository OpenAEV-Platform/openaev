package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousEventRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.ee.EnterpriseEditionService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with the {@code autonomous_*} tables activated, the tenant scope set from
 * the request isolates the autonomous-run endpoints: the owner tenant sees its run, timeline and
 * directives, a different tenant gets a 404 (the run row itself is invisible, so nothing hangs off
 * it), and a scope-less repository read is fail-closed. This exercises the whole chain (the {@code
 * TxCtx} binding, the transaction aspect, the {@code can_access_tenant} rewrite, and the JPA
 * reads), so it proves the isolation claim rather than the mere presence of a guard - the HTTP
 * complement to the inspector's own SQL-layer tests, mirroring {@code AttackPathHttpIsolationTest}.
 *
 * <p>Both scope routes are covered, because the orchestrator's callbacks ride the legacy
 * non-prefixed mapping: the tenant-prefixed path (operator UI) and the plain path where the scope
 * comes from the caller's membership / the {@code X-Tenant-Ids} header (XTM One service account).
 *
 * <p>Each test stays on a single tenant selection: the per-request scope is set once and the aspect
 * refuses to redefine it within one transaction.
 */
@Transactional
@TestPropertySource(
    properties = {
      "openaev.tenant.active-tables=autonomous_runs,autonomous_events,autonomous_directives"
    })
@WithMockUser(isAdmin = true)
@DisplayName("autonomous run isolation through the real HTTP endpoints")
class AutonomousRunHttpIsolationTest extends IntegrationTest {

  // Both mappings the API declares, derived from its own constants rather than retyped: a route
  // renamed on the controller must break this test, not silently stop covering it.
  private static final String SCOPED = TENANT_PREFIX + "/autonomous-runs";
  private static final String PLAIN = AutonomousRunApi.AUTONOMOUS_URI;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private AutonomousRunRepository runRepository;
  @Autowired private AutonomousEventRepository eventRepository;
  @Autowired private AutonomousDirectiveRepository directiveRepository;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String tenantB;
  private String runId;
  private String eventId;
  private String directiveId;

  @BeforeEach
  void seedRunUnderTenantA() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("auto-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("auto-iso-b").getId();
    runId = seedRun(tenantA);
    eventId = seedEvent(tenantA, runId);
    directiveId = seedDirective(tenantA, runId);
  }

  @Test
  @DisplayName("under the owner tenant's path: the run is visible")
  void getUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}", tenantA, runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autonomous_run_id").value(runId));
  }

  @Test
  @DisplayName("under another tenant's path: the same run does not exist (404, no leak)")
  void getUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}", tenantB, runId)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("the run list under the owner tenant's path carries the run")
  void listUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(SCOPED, tenantA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.autonomous_run_id=='" + runId + "')]").exists());
  }

  @Test
  @DisplayName("the run list under another tenant's path does not carry the owner's run")
  void listUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(SCOPED, tenantB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.autonomous_run_id=='" + runId + "')]").doesNotExist());
  }

  @Test
  @DisplayName("under the owner tenant's path: the decision timeline is visible")
  void timelineUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}/timeline", tenantA, runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].autonomous_event_id").value(eventId));
  }

  @Test
  @DisplayName("under another tenant's path: the timeline 404s with the hidden run")
  void timelineUnderOtherTenantIsHidden() throws Exception {
    // The timeline resolves the run first; a foreign tenant cannot even see the run row, so the
    // request dies on the lookup instead of leaking an (empty or not) timeline shape.
    mvc.perform(get(SCOPED + "/{runId}/timeline", tenantB, runId)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under the owner tenant's path: the steering directives are visible")
  void directivesUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}/directives", tenantA, runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].autonomous_directive_id").value(directiveId));
  }

  @Test
  @DisplayName("under another tenant's path: the directives 404 with the hidden run")
  void directivesUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}/directives", tenantB, runId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header (the orchestrator's route): the owner's run is visible")
  void getViaHeaderForOwnerTenantIsVisible() throws Exception {
    // Second scope-carrying route: the same handlers are also mapped without the tenant prefix
    // (the XTM One callbacks ride this one), and the scope then comes from the header instead of
    // the path. Different plumbing, so path coverage does not imply header coverage.
    mvc.perform(get(PLAIN + "/{runId}", runId).header("X-Tenant-Ids", tenantA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autonomous_run_id").value(runId));
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: another tenant selected, the run does not exist")
  void getViaHeaderForOtherTenantIsHidden() throws Exception {
    mvc.perform(get(PLAIN + "/{runId}", runId).header("X-Tenant-Ids", tenantB))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("no scope set: every autonomous read is empty although the rows exist (fail-closed)")
  void readWithoutScopeIsFailClosed() {
    // No TxCtx in this test transaction, so the aspect never set app.current_tenants and the
    // inspector denies every row on all three tables. The rows exist, they are only hidden.
    assertThat(runRepository.findById(runId)).isEmpty();
    assertThat(eventRepository.findByRunIdOrderBySequenceAsc(runId)).isEmpty();
    assertThat(directiveRepository.findByRunIdOrderByCreatedAtAsc(runId)).isEmpty();
    assertThat(rawCount("autonomous_runs", "autonomous_run_id", runId)).isEqualTo(1L);
    assertThat(rawCount("autonomous_events", "autonomous_event_id", eventId)).isEqualTo(1L);
    assertThat(rawCount("autonomous_directives", "autonomous_directive_id", directiveId))
        .isEqualTo(1L);
  }

  // Native seed, not the API: the setup seeds two tenants, and an explicit tenant_id lets the rows
  // land without a request scope while keeping the read cases independent of any write endpoint.
  // The run is seeded COMPLETED so the read-path reconcile is a no-op and no simulation is needed.
  private String seedRun(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                + " autonomous_run_objective, autonomous_run_status)"
                + " VALUES (:id, :tenant, 'Own the domain', 'COMPLETED')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedEvent(String tenantId, String runId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_events (autonomous_event_id, tenant_id,"
                + " autonomous_event_run_id, autonomous_event_sequence, autonomous_event_type,"
                + " autonomous_event_title)"
                + " VALUES (:id, :tenant, :run, 1, 'STATUS', 'Run created')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("run", runId)
        .executeUpdate();
    return id;
  }

  private String seedDirective(String tenantId, String runId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_directives (autonomous_directive_id, tenant_id,"
                + " autonomous_directive_run_id, autonomous_directive_content,"
                + " autonomous_directive_status)"
                + " VALUES (:id, :tenant, :run, 'Focus on the domain controller', 'PENDING')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("run", runId)
        .executeUpdate();
    return id;
  }

  // Ground truth, bypassing the scope: raw JDBC on the test's own connection sees the uncommitted
  // seed and the inspector does not rewrite a statement it never generated.
  private long rawCount(String table, String idColumn, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM " + table + " WHERE " + idColumn + " = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
