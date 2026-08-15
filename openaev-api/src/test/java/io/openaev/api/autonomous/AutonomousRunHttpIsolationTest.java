package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
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
 * <p>The write half is covered end-to-end too: creating a run, appending a timeline event and
 * queueing a directive through the real endpoints must stamp the raw {@code tenant_id} of every new
 * row with the selected / parent-run tenant. The inspector only guards reads - INSERT attribution
 * is explicit application code since {@code TenantBaseListener} was removed - so a missing {@code
 * setTenant} would pass any read-only suite and misattribute production data; these cases pin the
 * attribution at the column level and the refusal outside a valid scope.
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
  @DisplayName(
      "POST create under the selected tenant stamps the run and its event with that tenant")
  void createRunViaApiStampsSelectedTenant() throws Exception {
    // The real operator write path, end to end: the API provisions the scenario + plan substrate
    // and INSERTs the run row. The inspector cannot attribute an INSERT, so the explicit
    // attributeRunTenant/setTenant chain is what lands tenant_id - assert it at the raw column.
    String response =
        mvc.perform(
                post(SCOPED, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"objective\": \"Own the file server\", \"plan_mode\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_run_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdRunId = JsonPath.read(response, "$.autonomous_run_id");

    assertThat(rawTenantId("autonomous_runs", "autonomous_run_id", createdRunId))
        .isEqualTo(tenantA);
    // The creation narration (AutonomousEventService#doAppend) is attributed to the same tenant.
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", createdRunId))
        .isEqualTo(1L);
    assertThat(rawTenantId("autonomous_events", "autonomous_event_run_id", createdRunId))
        .isEqualTo(tenantA);
  }

  @Test
  @DisplayName("POST event on the orchestrator's route stamps the parent run's tenant")
  void recordEventViaCallbackRouteStampsParentRunTenant() throws Exception {
    // The exact callback write AutonomousEventService#doAppend serves: the tenant must come from
    // the parent run, never from a thread-local default (the orchestrator rides the non-prefixed
    // route, where the legacy TenantContext default would misattribute the row).
    String response =
        mvc.perform(
                post(PLAIN + "/{runId}/events", runId)
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\": \"DECISION\", \"title\": \"Pivot to the DC\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_event_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdEventId = JsonPath.read(response, "$.autonomous_event_id");

    assertThat(rawTenantId("autonomous_events", "autonomous_event_id", createdEventId))
        .isEqualTo(tenantA);
  }

  @Test
  @DisplayName("POST directive under the owner tenant stamps the run's tenant on both new rows")
  void addDirectiveViaApiStampsParentRunTenant() throws Exception {
    // Steering needs a live run (settled runs refuse directives) with no simulation so the write
    // stays free of chaining machinery. Seeded raw, mutated through the real endpoint.
    String activeRunId = seedActiveRun(tenantA);
    String response =
        mvc.perform(
                post(SCOPED + "/{runId}/directives", tenantA, activeRunId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\": \"Focus on lateral movement\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_directive_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdDirectiveId = JsonPath.read(response, "$.autonomous_directive_id");

    assertThat(rawTenantId("autonomous_directives", "autonomous_directive_id", createdDirectiveId))
        .isEqualTo(tenantA);
    // The "Operator directive queued" narration carries the same tenant as the directive.
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", activeRunId)).isEqualTo(1L);
    assertThat(rawTenantId("autonomous_events", "autonomous_event_run_id", activeRunId))
        .isEqualTo(tenantA);
  }

  @Test
  @DisplayName("POST event under another tenant's scope is refused and writes nothing")
  void recordEventOutsideParentTenantIsRefusedAndWritesNothing() throws Exception {
    // The parent run is invisible outside its tenant, so the write dies on the run lookup: a
    // foreign scope can never append into another tenant's timeline (fail-closed write).
    mvc.perform(
            post(PLAIN + "/{runId}/events", runId)
                .header("X-Tenant-Ids", tenantB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"DECISION\", \"title\": \"cross-tenant append\"}"))
        .andExpect(status().isNotFound());

    // Only the seeded event remains: nothing was written for the refused append.
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", runId)).isEqualTo(1L);
  }

  @Test
  @DisplayName("POST create outside a single valid write scope is refused (400), no run written")
  void createOutsideValidWriteScopeIsRefusedAndWritesNothing() throws Exception {
    // A two-tenant selection on the plain route: the auto-provisioned scenario lands in the
    // caller's legacy default tenant (no path prefix sets TenantContext), which is outside the
    // selected {A, B} scope, so TenantWriteScopeResolver refuses the attribution with 400
    // (TENANT_WRITE_SCOPE) before the run INSERT - same contract as an ambiguous bare create,
    // whose multi-tenant scope cannot pin the row to one tenant either.
    mvc.perform(
            post(PLAIN)
                .header("X-Tenant-Ids", tenantA + "," + tenantB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objective\": \"ambiguous-scope-objective\", \"plan_mode\": true}"))
        .andExpect(status().isBadRequest());

    assertThat(rawCount("autonomous_runs", "autonomous_run_objective", "ambiguous-scope-objective"))
        .isEqualTo(0L);
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

  // A live (steerable) run for the write cases: directives are refused on a settled run, and the
  // run carries no simulation so no reconcile / chaining machinery is dragged into the write.
  private String seedActiveRun(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                + " autonomous_run_objective, autonomous_run_status)"
                + " VALUES (:id, :tenant, 'Own the domain', 'RUNNING')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
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

  // Ground truth for write attribution: the raw tenant_id column of a row the API just wrote,
  // read over plain JDBC on the test's own connection (sees uncommitted rows, no inspector
  // rewrite). Null when the row does not exist, so a missing row fails the equality loudly.
  private String rawTenantId(String table, String idColumn, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
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
