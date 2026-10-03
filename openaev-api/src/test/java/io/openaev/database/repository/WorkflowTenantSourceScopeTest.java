package io.openaev.database.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The chaining engine stamps its queue events with a tenant resolved by two native projection
 * queries, {@code WorkflowRepository.findTenantIdByWorkflowId} and {@code
 * StepRepository.findTenantIdByStepId}. Both reach the tenant by joining {@code exercises}, and
 * both are documented as context-free because a native query escapes the Hibernate {@code
 * tenantFilter}.
 *
 * <p>This pins what that property becomes once {@code exercises} is tenant-active: the statement
 * inspector rewrites raw SQL, so being native is no exemption, and the resolution turns into a
 * scope-dependent read. A stamp that resolves to nothing is not an error: the consumers fall back
 * to the default tenant, so a scope-less or foreign-scoped producer silently moves a whole step run
 * into another tenant.
 *
 * <p>It also pins the transactional state at the call site, which is the cost of resolving the
 * stamp under an explicit intention: the background primitive refuses to open inside an active
 * transaction, and the publisher always runs inside one.
 *
 * <p>Deliberately NOT {@code @Transactional}: the primitive opens its own transactions and refuses
 * a test-managed one. Seeding and cleanup run in auto-committed JDBC, which bypasses Hibernate and
 * is therefore unaffected by the armed table.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=exercises")
@DisplayName("chaining tenant source: what the stamp resolves to once exercises is tenant-active")
class WorkflowTenantSourceScopeTest extends IntegrationTest {

  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String simulationWorkflowA;
  private String stepA;
  private String scenarioWorkflowA;
  private String simulationA;

  @BeforeEach
  void seedOneSimulationBackedAndOneScenarioBackedWorkflow() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("chaining-source-a-");
    tenantB = seedTenant("chaining-source-b-");

    simulationWorkflowA = seedSimulationWorkflow(tenantA);
    stepA = seedStep(simulationWorkflowA);
    scenarioWorkflowA = seedScenarioWorkflow(tenantA);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM steps WHERE step_id = ?", stepA);
    jdbc.update(
        "DELETE FROM workflows WHERE workflow_id IN (?, ?)",
        simulationWorkflowA,
        scenarioWorkflowA);
    jdbc.update("DELETE FROM exercises WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM scenarios WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @Nested
  @DisplayName("the workflow projection used by the ready-event publisher")
  class WorkflowProjection {

    @Test
    @DisplayName("resolves nothing when the publisher's transaction carries no scope")
    void given_noScope_should_resolveNoTenantForTheWorkflow() {
      // Arrange: the row exists, only the scope differs between this case and the next ones.
      assertTrue(workflowRowExists(simulationWorkflowA), "ground truth: the workflow row is there");

      // Act
      Optional<String> resolved =
          inRawTransaction(status -> findWorkflowTenant(simulationWorkflowA));

      // Assert
      assertEquals(
          Optional.empty(),
          resolved,
          "an active exercises table makes the native projection fail closed with no scope");
    }

    @Test
    @DisplayName("resolves nothing when the transaction is scoped to another tenant")
    void given_aForeignTenantScope_should_resolveNoTenantForTheWorkflow() {
      // Act
      Optional<String> resolved =
          tenantTx.execute(TxCtx.forTenant(tenantB), () -> findWorkflowTenant(simulationWorkflowA));

      // Assert
      assertEquals(
          Optional.empty(),
          resolved,
          "a producer carrying the wrong tenant gets an empty stamp, not an error");
    }

    @Test
    @DisplayName("resolves the owning tenant under an all-tenants intention")
    void given_anAllTenantsIntention_should_resolveTheOwningTenantForTheWorkflow() {
      // Act
      Optional<String> resolved =
          tenantTx.execute(TxCtx.allTenants(), () -> findWorkflowTenant(simulationWorkflowA));

      // Assert
      assertEquals(
          Optional.of(tenantA), resolved, "an explicit intention restores the context-free stamp");
    }

    @Test
    @DisplayName("resolves nothing for a scenario-backed workflow, whatever the scope")
    void given_aScenarioBackedWorkflow_should_resolveNoTenant() {
      // Act
      Optional<String> resolved =
          tenantTx.execute(TxCtx.allTenants(), () -> findWorkflowTenant(scenarioWorkflowA));

      // Assert: the projection joins exercises only, so a scenario-owned workflow has no stamp even
      // under the widest scope. Independent of activation.
      assertEquals(
          Optional.empty(), resolved, "the projection reaches the tenant through exercises");
    }
  }

  @Nested
  @DisplayName("the step projection used by the update-event publisher")
  class StepProjection {

    @Test
    @DisplayName("resolves nothing when the publisher's transaction carries no scope")
    void given_noScope_should_resolveNoTenantForTheStep() {
      // Act
      Optional<String> resolved = inRawTransaction(status -> findStepTenant(stepA));

      // Assert
      assertEquals(
          Optional.empty(),
          resolved,
          "same shape as the workflow projection, same fail-closed read");
    }

    @Test
    @DisplayName("resolves the owning tenant under an all-tenants intention")
    void given_anAllTenantsIntention_should_resolveTheOwningTenantForTheStep() {
      // Act
      Optional<String> resolved = tenantTx.execute(TxCtx.allTenants(), () -> findStepTenant(stepA));

      // Assert
      assertEquals(Optional.of(tenantA), resolved);
    }
  }

  @Nested
  @DisplayName("a workflow run still uncommitted in the publisher's transaction")
  class UncommittedRun {

    @Test
    @DisplayName("resolves when the intention is set on the publisher's own transaction")
    void given_theScopeOnTheSameTransaction_should_resolveTheOwningTenant() {
      // Arrange + Act: the launch path creates the RUN workflow and publishes its ready events in
      // one transaction, so the row the stamp is resolved from is not committed yet.
      Optional<String> resolved =
          inRawTransaction(
              status -> {
                String uncommitted = insertRunWorkflowFor(simulationA);
                tenantTx.setScopeOnCurrentTransaction(TxCtx.allTenants());
                Optional<String> seen = findWorkflowTenant(uncommitted);
                status.setRollbackOnly();
                return seen;
              });

      // Assert
      assertEquals(Optional.of(tenantA), resolved, "the row is visible inside its own transaction");
    }

    @Test
    @DisplayName("resolves nothing when the intention opens a separate transaction")
    void given_anIntentionInANewTransaction_should_resolveNoTenant() {
      // Act: REQUIRES_NEW suspends the caller and reads on another connection, with its own
      // snapshot.
      Optional<String> resolved =
          inRawTransaction(
              status -> {
                String uncommitted = insertRunWorkflowFor(simulationA);
                Optional<String> seen =
                    tenantTx.executeNew(TxCtx.allTenants(), () -> findWorkflowTenant(uncommitted));
                status.setRollbackOnly();
                return seen;
              });

      // Assert: an empty stamp, which the consumers read as "no tenant" and replace with the
      // default one.
      assertEquals(
          Optional.empty(),
          resolved,
          "a nested transaction cannot see the workflow row its caller has not committed");
    }
  }

  /**
   * What this nest does NOT establish: that a transaction is always open where the publisher is
   * reached. It is not. {@code BatchingInjectStatusService.handleInjectExecutionCallback} is
   * {@code @Transactional(propagation = NOT_SUPPORTED)} and returns from its per-tenant
   * transactions before its {@code @WorkflowUpdateEvent} advice calls {@code
   * QueueChainingService.updateStep}, so that publisher path reaches the resolution with no
   * transaction at all. Both shapes of cost therefore apply to wrapping the resolution in an
   * explicit intention: it is refused where a transaction is open, and it has to open one where
   * none is, and that is the argument for reading the tenant off the row instead.
   */
  @Nested
  @DisplayName("the transactional state at one of the publisher's call sites")
  class CallSite {

    @Test
    @DisplayName("refuses to open the background primitive inside a caller's transaction")
    void given_anActiveTransaction_should_refuseToOpenAnIntention() {
      // Act: the publisher paths that run inside a transaction, which is some of them, not all.
      IllegalStateException refusal =
          assertThrows(
              IllegalStateException.class,
              () ->
                  inRawTransaction(
                      status ->
                          tenantTx.execute(
                              TxCtx.allTenants(), () -> findWorkflowTenant(simulationWorkflowA))));

      // Assert
      assertTrue(
          refusal.getMessage().contains("refuses to open inside an active transaction"),
          "the intention cannot simply be wrapped around the resolution: " + refusal.getMessage());
    }
  }

  private Optional<String> findWorkflowTenant(String workflowId) {
    return workflowRepository.findTenantIdByWorkflowId(workflowId);
  }

  private Optional<String> findStepTenant(String stepId) {
    return stepRepository.findTenantIdByStepId(stepId);
  }

  private <T> T inRawTransaction(
      org.springframework.transaction.support.TransactionCallback<T> work) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    return template.execute(work);
  }

  private String insertRunWorkflowFor(String simulationId) {
    String workflowId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_version,"
            + " workflow_simulation_id) VALUES (?, CAST(? AS workflow_status), ?, ?)",
        workflowId,
        "RUN",
        0,
        simulationId);
    return workflowId;
  }

  private boolean workflowRowExists(String workflowId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM workflows WHERE workflow_id = ?", Long.class, workflowId);
    return count != null && count == 1L;
  }

  private String seedTenant(String prefix) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        prefix + id);
    return id;
  }

  private String seedSimulationWorkflow(String tenantId) {
    String simulationId = UUID.randomUUID().toString();
    simulationA = simulationId;
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, tenant_id)"
            + " VALUES (?, ?, ?, ?)",
        simulationId,
        "chaining source simulation",
        "noreply@filigran.io",
        tenantId);
    String workflowId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_version,"
            + " workflow_simulation_id) VALUES (?, CAST(? AS workflow_status), ?, ?)",
        workflowId,
        "RUN",
        0,
        simulationId);
    return workflowId;
  }

  private String seedScenarioWorkflow(String tenantId) {
    String scenarioId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
            + " VALUES (?, ?, ?, ?)",
        scenarioId,
        "chaining source scenario",
        "noreply@filigran.io",
        tenantId);
    String workflowId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_version,"
            + " workflow_scenario_id) VALUES (?, CAST(? AS workflow_status), ?, ?)",
        workflowId,
        "RUN",
        0,
        scenarioId);
    return workflowId;
  }

  private String seedStep(String workflowId) {
    String stepId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO steps (step_id, step_action_class, step_limit_execution, step_status,"
            + " step_workflow_id) VALUES (?, CAST(? AS step_action_class), ?, CAST(? AS step_status),"
            + " ?)",
        stepId,
        "INJECT_EXECUTION",
        1,
        "RUN",
        workflowId);
    return stepId;
  }
}
