package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.database.model.Step;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Drives {@link AttackPathVerdictSyncService} the way the chaining engine does when a step's
 * expectation results land (#6647, spec 002, FR5): frozen execution rows for the step, an
 * expectation carrying a resolved score, and the tenant taken from the inject.
 *
 * <p>Two properties matter beyond "the status is written". First, the row must be stamped with the
 * simulation's new version — an unstamped verdict is invisible to every polling client, which is
 * the failure mode this whole feature exists to remove. Second, replaying the same result must
 * change nothing at all, version included: expectation results arrive repeatedly during a run, and
 * a projection that re-stamps on every replay would push a change to every client on every event.
 *
 * <p>Ground truth is read with raw JDBC, so a missing or misplaced write cannot hide behind a
 * scoped read. Deliberately not {@code @Transactional}: the sync commits in its own scoped
 * transaction.
 */
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH",
      "openaev.tenant.active-tables=attackpath_execution,attackpath_finding"
    })
@WithMockUser(isAdmin = true)
@DisplayName("attack path: expectation verdicts reach the projection, once")
class AttackPathVerdictSyncTest extends IntegrationTest {

  private static final String STEP_ID = "step-verdict-sync";
  private static final String AGENT_ID = "agent-verdict-1";
  private static final String ASSET_ID = "asset-verdict-1";

  @Autowired private AttackPathVerdictSyncService verdictSyncService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private Tenant tenant;
  private String simulationId;
  private String manualTargets;

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("ap-verdict");
    simulationId = "SIM-VERDICT-" + UUID.randomUUID();
  }

  @AfterEach
  void cleanUp() {
    jdbc.update(
        "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?",
        simulationId);
    jdbc.update(
        "DELETE FROM attackpath_graph_version WHERE attackpath_graph_version_simulation_id = ?",
        simulationId);
    tenantHelper.deleteCommittedTenants(tenant.getId());
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "an agent-level prevention verdict lands on that agent's row, stamped with a version")
  void agentLevelPreventionVerdictIsWrittenAndStamped() {
    freezeExecution("exec-1", AGENT_ID, ASSET_ID);

    sync(prevention(agent(AGENT_ID), null, 100.0));

    assertThat(preventionStatus("exec-1")).isEqualTo("Prevented");
    assertThat(rowVersion("exec-1")).isEqualTo(currentVersion());
    assertThat(currentVersion()).isPositive();
  }

  @Test
  @DisplayName("an asset-level detection verdict lands on the rows targeting that asset")
  void assetLevelDetectionVerdictIsWrittenByTargetAsset() {
    freezeExecution("exec-1", AGENT_ID, ASSET_ID);
    freezeExecution("exec-2", "other-agent", "other-asset");

    sync(detection(null, endpoint(ASSET_ID), 100.0));

    assertThat(detectionStatus("exec-1")).isEqualTo("Detected");
    assertThat(detectionStatus("exec-2")).isNull();
  }

  @Test
  @DisplayName("two asset-level verdicts of one type land on both rows under the same version")
  void assetLevelVerdictsAreAppliedTogether() {
    // Several assets carrying one verdict is what an asset-group expectation expands into, and what
    // the batched IN-list update exists for. What this pins is the behaviour that batching must not
    // change: every targeted row gets the verdict, under the single version the batch took, and
    // rows
    // outside the set are left alone.
    freezeExecution("exec-1", AGENT_ID, ASSET_ID);
    freezeExecution("exec-2", "other-agent", "other-asset");

    sync(
        detection(null, endpoint(ASSET_ID), 100.0),
        detection(null, endpoint("other-asset"), 100.0));

    assertThat(detectionStatus("exec-1")).isEqualTo("Detected");
    assertThat(detectionStatus("exec-2")).isEqualTo("Detected");
    assertThat(rowVersion("exec-1")).isEqualTo(rowVersion("exec-2")).isEqualTo(currentVersion());
  }

  @Test
  @DisplayName("an inject-level verdict lands on the discovered target rows of a manual inject")
  void injectLevelVerdictIsAppliedByTargetKey() {
    // The rows the ingestion writes for a manual injector target have no asset id and no agent, so
    // neither of the other two granularities can reach them; the expectation names nothing either,
    // and the keys come from the inject content, exactly as the ingestion derived them.
    freezeDiscoveredExecution("exec-raw-1", "10.0.0.1");
    freezeDiscoveredExecution("exec-raw-2", "10.0.0.2");
    freezeExecution("exec-asset", AGENT_ID, ASSET_ID);

    withManualTargets("10.0.0.1,10.0.0.2");
    sync(prevention(null, null, 100.0));

    assertThat(preventionStatus("exec-raw-1")).isEqualTo("Prevented");
    assertThat(preventionStatus("exec-raw-2")).isEqualTo("Prevented");
    assertThat(rowVersion("exec-raw-1")).isEqualTo(currentVersion());
    // The asset row is not a target of this inject's manual selector, so it stays untouched.
    assertThat(preventionStatus("exec-asset")).isNull();
  }

  @Test
  @DisplayName("an inject-level verdict on an inject with no manual target is dropped")
  void injectLevelVerdictWithoutTargetKeysIsDropped() {
    freezeExecution("exec-1", AGENT_ID, ASSET_ID);

    sync(prevention(null, null, 100.0));

    assertThat(preventionStatus("exec-1")).isNull();
    assertThat(versionRowCount()).isZero();
  }

  @Test
  @DisplayName("a failed expectation writes the failure label, not silence")
  void failedExpectationWritesTheFailureLabel() {
    freezeExecution("exec-1", AGENT_ID, ASSET_ID);

    sync(prevention(agent(AGENT_ID), null, 0.0));

    assertThat(preventionStatus("exec-1")).isEqualTo("Not Prevented");
  }

  @Test
  @DisplayName("replaying the identical result changes nothing, version stamp included")
  void replayingTheSameResultIsANoOp() {
    freezeExecution("exec-1", AGENT_ID, ASSET_ID);
    sync(prevention(agent(AGENT_ID), null, 100.0));
    long stampedVersion = rowVersion("exec-1");

    sync(prevention(agent(AGENT_ID), null, 100.0));

    assertThat(preventionStatus("exec-1")).isEqualTo("Prevented");
    // The guarded WHERE matched zero rows, so the row keeps its original version and no client is
    // told anything changed — even though the batch itself bumped the counter.
    assertThat(rowVersion("exec-1")).isEqualTo(stampedVersion);
    assertThat(currentVersion()).isGreaterThan(stampedVersion);
  }

  @Test
  @DisplayName("a verdict that later changes is written again, with a newer version")
  void aChangedVerdictIsWrittenAgain() {
    freezeExecution("exec-1", AGENT_ID, ASSET_ID);
    sync(prevention(agent(AGENT_ID), null, 0.0));
    long firstVersion = rowVersion("exec-1");

    sync(prevention(agent(AGENT_ID), null, 100.0));

    assertThat(preventionStatus("exec-1")).isEqualTo("Prevented");
    assertThat(rowVersion("exec-1")).isGreaterThan(firstVersion);
  }

  @Test
  @DisplayName("an expectation with no score yet writes nothing and does not bump the version")
  void aPendingExpectationIsNotWritten() {
    freezeExecution("exec-1", AGENT_ID, ASSET_ID);

    sync(prevention(agent(AGENT_ID), null, null));

    assertThat(preventionStatus("exec-1")).isNull();
    assertThat(rowVersion("exec-1")).isZero();
    assertThat(versionRowCount()).isZero();
  }

  // -- helpers --

  private void sync(BaseInjectExpectation... expectations) {
    TenantContext.setCurrentTenant(tenant.getId());
    Step step = new Step();
    step.setId(STEP_ID);
    verdictSyncService.sync(step, inject(), List.of(expectations));
    TenantContext.clearCurrentTenant();
  }

  /** The inject as the chaining seam hands it over: its tenant, simulation and content are read. */
  private Inject inject() {
    Inject inject = new Inject();
    inject.setTenant(tenant);
    Exercise exercise = new Exercise();
    exercise.setId(simulationId);
    inject.setExercise(exercise);
    if (manualTargets != null) {
      ObjectNode content = JsonNodeFactory.instance.objectNode();
      content.put("target_selector", "manual");
      content.put("targets", manualTargets);
      inject.setContent(content);
    }
    return inject;
  }

  /** Makes the next {@link #sync} run against an injector inject with manual (raw) targets. */
  private void withManualTargets(String targets) {
    manualTargets = targets;
  }

  private TechnicalInjectExpectation prevention(Agent agent, Endpoint asset, Double score) {
    return technical(new PreventionInjectExpectation(), agent, asset, score);
  }

  private TechnicalInjectExpectation detection(Agent agent, Endpoint asset, Double score) {
    return technical(new DetectionInjectExpectation(), agent, asset, score);
  }

  private TechnicalInjectExpectation technical(
      TechnicalInjectExpectation expectation, Agent agent, Endpoint asset, Double score) {
    expectation.setAgent(agent);
    expectation.setAsset(asset);
    expectation.setExpectedScore(100.0);
    expectation.setScore(score);
    return expectation;
  }

  private Agent agent(String id) {
    Agent agent = new Agent();
    agent.setId(id);
    return agent;
  }

  private Endpoint endpoint(String id) {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(id);
    return endpoint;
  }

  private void freezeExecution(String executionId, String agentId, String assetId) {
    jdbc.update(
        "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
            + " attackpath_execution_simulation_id, attackpath_execution_step_id,"
            + " attackpath_execution_agent_id, attackpath_execution_source_kind,"
            + " attackpath_execution_target_kind, attackpath_execution_target_asset_id,"
            + " attackpath_execution_target_key, attackpath_execution_executed_at)"
            + " VALUES (?, ?, ?, ?, ?, 'AGENT', 'ASSET', ?, ?, now())",
        executionId,
        tenant.getId(),
        simulationId,
        STEP_ID,
        agentId,
        assetId,
        assetId);
  }

  /** A row as the ingestion freezes it for a discovered (raw) target: no agent, no asset id. */
  private void freezeDiscoveredExecution(String executionId, String targetKey) {
    jdbc.update(
        "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
            + " attackpath_execution_simulation_id, attackpath_execution_step_id,"
            + " attackpath_execution_source_kind, attackpath_execution_target_kind,"
            + " attackpath_execution_target_key, attackpath_execution_executed_at)"
            + " VALUES (?, ?, ?, ?, 'INJECTOR', 'DISCOVERED', ?, now())",
        executionId,
        tenant.getId(),
        simulationId,
        STEP_ID,
        targetKey);
  }

  private String preventionStatus(String executionId) {
    return column("attackpath_execution_prevention_status", executionId, String.class);
  }

  private String detectionStatus(String executionId) {
    return column("attackpath_execution_detection_status", executionId, String.class);
  }

  private long rowVersion(String executionId) {
    return column("attackpath_execution_row_version", executionId, Long.class);
  }

  private <T> T column(String name, String executionId, Class<T> type) {
    return jdbc.queryForObject(
        "SELECT " + name + " FROM attackpath_execution WHERE attackpath_execution_id = ?",
        type,
        executionId);
  }

  private long currentVersion() {
    return jdbc.queryForObject(
        "SELECT attackpath_graph_version_value FROM attackpath_graph_version"
            + " WHERE attackpath_graph_version_simulation_id = ?",
        Long.class,
        simulationId);
  }

  private int versionRowCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_graph_version"
            + " WHERE attackpath_graph_version_simulation_id = ?",
        Integer.class,
        simulationId);
  }
}
