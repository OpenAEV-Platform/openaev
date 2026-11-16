package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Isolation of {@code workflow_states} and {@code steps_delay_queue} on their own rows, at the
 * chaining services.
 *
 * <p>Both tables are the chaining engine's run state: no endpoint reads them, and the engine reads
 * them through {@link WorkflowStateStore} and {@link StepDelayQueueService} by the id of a workflow
 * run. The case is therefore those services under one tenant's scope. Every row hangs off a run,
 * and a run of another tenant is already refused on {@code workflows}, so the other tenant's state
 * and delay entry are attached to the caller's own runs: the parent is visible and only the child
 * table's own scope stands between the caller and the row. The seed is raw JDBC with an explicit
 * tenant, which the statement inspector never rewrites and which the engine would not produce.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction.
 * The seed is committed and swept after each test; deleting a tenant cascades to its rows.
 */
@CoversTenantIsolation({"workflow_states", "steps_delay_queue"})
@WithMockUser(isAdmin = true)
@DisplayName("workflow_states and steps_delay_queue refuse another tenant's row on a run")
class ChainingRunStateScopeIsolationTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private WorkflowStateStore workflowStateStore;
  @Autowired private StepDelayQueueService stepDelayQueueService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private final List<String> seededTenants = new ArrayList<>();
  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String runA;
  private String stepTemplateA;

  @BeforeEach
  void seedTwoTenantsAndOneRunInA() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant();
    tenantB = seedTenant();
    runA = seedRun(tenantA);
    stepTemplateA = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO steps (step_id, step_action_class, step_limit_execution, step_status,"
            + " step_workflow_id, tenant_id)"
            + " VALUES (?, 'INJECT_EXECUTION', 1, 'TEMPLATE', ?, ?)",
        stepTemplateA,
        runA,
        tenantA);
  }

  @AfterEach
  void sweep() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Nested
  @DisplayName("workflow_states")
  class States {

    @Test
    @DisplayName("given a run id, should read its own global state and not the other's")
    void given_runId_should_readOwnGlobalStateAndNotOtherTenants() {
      // Arrange
      String otherRunA = seedRun(tenantA);
      String ownState = seedGlobalState(tenantA, runA);
      seedGlobalState(tenantB, otherRunA);

      // Act
      List<Optional<String>> found =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () ->
                  List.of(
                      workflowStateStore.findGlobalStateId(runA),
                      workflowStateStore.findGlobalStateId(otherRunA)));

      // Assert
      assertEquals(Optional.of(ownState), found.get(0), "A's state must be read under A's scope");
      assertTrue(found.get(1).isEmpty(), "B's state must not be read under A's scope");
    }
  }

  @Nested
  @DisplayName("steps_delay_queue")
  class DelayQueue {

    private String ownEntry;
    private String foreignEntry;

    @BeforeEach
    void seedOneDelayEntryPerTenantOnTheSameRun() {
      ownEntry = seedDelayEntry(tenantA, "own");
      foreignEntry = seedDelayEntry(tenantB, "foreign");
    }

    @Test
    @DisplayName("given its run, should list its own delay entry and not the other's")
    void given_run_should_listOwnEntryAndNotOtherTenants() {
      // Act
      List<String> entries =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () ->
                  stepDelayQueueService.findAllByWorkflowRun(runOfA()).stream()
                      .map(StepDelayQueue::getId)
                      .toList());

      // Assert
      assertEquals(List.of(ownEntry), entries, "only A's entry is listed under A's scope");
    }

    @Test
    @DisplayName("given its run, should delete its own delay entry and not the other's")
    void given_run_should_deleteOwnEntryAndNotOtherTenants() {
      // Act
      tenantTx.execute(
          TxCtx.forTenant(tenantA),
          () ->
              stepDelayQueueService.deleteAllByWorkflowRun(
                  runOfA(), WorkflowEndService.WORKFLOW_END_CAUSE.CANCELED));

      // Assert
      assertEquals(0, rawDelayEntryCount(ownEntry), "A's entry is deleted");
      assertEquals(1, rawDelayEntryCount(foreignEntry), "B's entry must be untouched");
    }
  }

  // -- FIXTURES --

  private Workflow runOfA() {
    return workflowRepository.findById(runA).orElseThrow();
  }

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "chaining-run-state-iso-" + id);
    seededTenants.add(id);
    return id;
  }

  private String seedRun(String tenantId) {
    String id = UUID.randomUUID().toString();
    String scenarioId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id) VALUES (?, ?, ?)",
        scenarioId,
        "chaining-run-state-iso-" + scenarioId,
        tenantId);
    // A workflow belongs to a simulation or a scenario; the services under test read neither.
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_version,"
            + " workflow_scenario_id, tenant_id) VALUES (?, 'RUN', 1, ?, ?)",
        id,
        scenarioId,
        tenantId);
    return id;
  }

  private String seedGlobalState(String tenantId, String runId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO workflow_states (workflow_state_id, workflow_execution_id, tenant_id)"
            + " VALUES (?, ?, ?)",
        id,
        runId,
        tenantId);
    return id;
  }

  private String seedDelayEntry(String tenantId, String input) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO steps_delay_queue (steps_delay_queue_id, steps_delay_queue_input,"
            + " steps_delay_queue_now, steps_delay_queue_delay, steps_delay_queue_step_template_id,"
            + " steps_delay_queue_workflow_run_id, tenant_id) VALUES (?, ?, now(), 0, ?, ?, ?)",
        id,
        input,
        stepTemplateA,
        runA,
        tenantId);
    return id;
  }

  private int rawDelayEntryCount(String entryId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM steps_delay_queue WHERE steps_delay_queue_id = ?",
            Integer.class,
            entryId);
    return count == null ? 0 : count;
  }
}
