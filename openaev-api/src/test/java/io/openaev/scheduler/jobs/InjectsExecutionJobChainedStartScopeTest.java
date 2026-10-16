package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.chaining.StepEvent;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.service.queue.QueueExecution;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.KeepRabbit;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A scheduled chained simulation is promoted to RUNNING by {@code InjectsExecutionJob}, which then
 * starts its workflow run. The start expands every ready step per in-scope asset, and the assets
 * table is tenant-isolated: read with no tenant scope it returns nothing, so the step is readied
 * once with no asset target instead of once per asset of the scope.
 *
 * <p>The tests arm the production list plus {@code injectors_contracts}, and run the job on a bare
 * thread, as a Quartz worker does: no transaction, no session, no ambient tenant. Not
 * {@code @Transactional}: the scoped primitive refuses to open inside an active transaction, so
 * rows are committed through JDBC in a tenant of their own and removed after each test.
 *
 * <p>The ready queue is shared and live. Its consumer is swapped for one that acknowledges and
 * drops, so the readied step stays READY and no inject is created behind the assertions.
 */
@KeepRabbit
@WithMockUser(isAdmin = true)
@DisplayName("InjectsExecutionJob starting a scheduled chained simulation")
class InjectsExecutionJobChainedStartScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTablesWithInjectorContracts(DynamicPropertyRegistry registry) {
    TreeSet<String> tables = new TreeSet<>(ProductionActiveTables.names());
    tables.add("injectors_contracts");
    registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
  }

  @Autowired private InjectsExecutionJob injectsExecutionJob;
  @Autowired private QueueChainingService queueChainingService;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String assetId;
  private String contractId;
  private String exerciseId;
  private QueueExecution<StepEvent> previousReadyConsumer;

  @BeforeEach
  void seedAScheduledChainedSimulation() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    assetId = UUID.randomUUID().toString();
    contractId = UUID.randomUUID().toString();
    exerciseId = UUID.randomUUID().toString();
    String workflowId = UUID.randomUUID().toString();

    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "chained-start-scope-" + tenantId);
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, 'chained-start-asset', 'Endpoint', now(), now(), ?,"
            + " 'chained-start-asset', 'Linux', 'x86_64')",
        assetId,
        tenantId);
    // An asset field makes the contract asset-centric, so the start expands it per scoped asset
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " tenant_id) VALUES (?, ?, ?)",
        contractId,
        "{\"fields\":[{\"key\":\"assets\",\"type\":\"asset\"}]}",
        tenantId);
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_status,"
            + " exercise_start_date, tenant_id) VALUES (?, ?, ?, 'SCHEDULED', ?, ?)",
        exerciseId,
        "chained-start-scope",
        "chained-start@openaev.invalid",
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        tenantId);
    jdbc.update(
        // tenant_id is NOT NULL on the chaining tables since #7900, and these seeds bypass JPA,
        // so each one carries the tenant of the simulation the workflow belongs to.
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_version,"
            + " workflow_simulation_id, tenant_id) VALUES (?, 'TEMPLATE', 0, ?, ?)",
        workflowId,
        exerciseId,
        tenantId);
    jdbc.update(
        "INSERT INTO workflow_scope_rules (workflow_scope_rule_id,"
            + " workflow_scope_rule_selected_mode, workflow_scope_rule_source,"
            + " workflow_scope_rule_value, workflow_scope_rule_value_type, workflow_id,"
            + " tenant_id) VALUES (?, 'ALLOWLIST', 'ASSET', ?, 'ASSET_ID', ?, ?)",
        UUID.randomUUID().toString(),
        assetId,
        workflowId,
        tenantId);
    // No condition and no contract snapshot: the step is ready at once, and its targeting is
    // resolved from the live contract
    jdbc.update(
        "INSERT INTO steps (step_id, step_action_class, step_status, step_limit_execution,"
            + " step_data, step_workflow_id, tenant_id) VALUES (?, 'INJECT_EXECUTION',"
            + " 'TEMPLATE', 1, CAST(? AS jsonb), ?, ?)",
        UUID.randomUUID().toString(),
        "{\"inject_injector_contract\":{\"injector_contract_id\":\"" + contractId + "\"}}",
        workflowId,
        tenantId);

    previousReadyConsumer = readyConsumer();
    queueChainingService.setCallbackForReadyQueue(events -> events);
  }

  @AfterEach
  void sweep() throws Exception {
    // Drop what the start published before the previous consumer comes back
    readyQueue().purge();
    queueChainingService.setCallbackForReadyQueue(previousReadyConsumer);
    // Deleting the workflows cascades to their steps, scope rules, states and delay queue rows
    jdbc.update("DELETE FROM workflows WHERE workflow_simulation_id = ?", exerciseId);
    jdbc.update(
        "DELETE FROM execution_traces WHERE execution_inject_status_id IN (SELECT status_id FROM"
            + " injects_statuses WHERE status_inject IN (SELECT inject_id FROM injects WHERE"
            + " tenant_id = ?))",
        tenantId);
    jdbc.update(
        "DELETE FROM injects_statuses WHERE status_inject IN"
            + " (SELECT inject_id FROM injects WHERE tenant_id = ?)",
        tenantId);
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM injectors_contracts WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  @SuppressWarnings("unchecked")
  private BatchQueueService<StepEvent> readyQueue() {
    return (BatchQueueService<StepEvent>)
        ReflectionTestUtils.getField(queueChainingService, "readyQueueService");
  }

  @SuppressWarnings("unchecked")
  private QueueExecution<StepEvent> readyConsumer() {
    return (QueueExecution<StepEvent>) ReflectionTestUtils.getField(readyQueue(), "queueExecution");
  }

  private static void onBareThread(ThrowingRunnable work) throws InterruptedException {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                work.run();
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the job failed", failure.get());
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private List<String> readyStepAssetTargets() {
    return jdbc.query(
        "SELECT s.step_data -> '_chaining_target' ->> 'assetId' FROM steps s"
            + " JOIN workflows w ON w.workflow_id = s.step_workflow_id"
            + " WHERE w.workflow_simulation_id = ? AND w.workflow_status = 'RUN'"
            + " AND s.step_status = 'READY'",
        (rs, rowNum) -> rs.getString(1),
        exerciseId);
  }

  // A nested class needs its own marker: the listener reads it from the class it is closing.
  @Nested
  @KeepRabbit
  @DisplayName("execute")
  class Execute {

    @Test
    @DisplayName("given a due chained simulation, should start its run and ready the step")
    void given_dueChainedSimulation_should_startItsRunAndReadyTheStep() throws Exception {
      // Arrange: the seeded simulation, SCHEDULED with a start date in the past

      // Act
      onBareThread(() -> injectsExecutionJob.execute(null));

      // Assert
      assertThat(
              jdbc.queryForObject(
                  "SELECT exercise_status FROM exercises WHERE exercise_id = ?",
                  String.class,
                  exerciseId))
          .isEqualTo("RUNNING");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM workflows WHERE workflow_simulation_id = ?"
                      + " AND workflow_status = 'RUN'",
                  Integer.class,
                  exerciseId))
          .isEqualTo(1);
      assertThat(readyStepAssetTargets()).hasSize(1);
    }

    @Test
    @DisplayName(
        "given an asset in the workflow scope, should ready the step on that asset of its tenant")
    void given_assetInTheWorkflowScope_should_readyTheStepOnThatAsset() throws Exception {
      // Arrange: an allowlisted asset of the simulation's tenant, resolvable only under its scope

      // Act
      onBareThread(() -> injectsExecutionJob.execute(null));

      // Assert: the ready step is bound to the scoped asset, not left without a target
      assertThat(readyStepAssetTargets()).containsExactly(assetId);
    }
  }
}
