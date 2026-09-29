package io.openaev.service.attackpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Step;
import io.openaev.database.repository.attackpath.AttackPathExecutionCollectorRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRemediationRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * attackpath_execution_collector / attackpath_execution_remediation read isolation (v2 activation,
 * no dedicated CRUD API for either table: both are read-only snapshot tables written by {@code
 * AttackPathExecutionIngestionService} and read by {@code AttackPathGraphService} and {@code
 * AttackPathSecurityPlatformResolver}). Exercised at the repository layer, on the real Spring
 * context and Postgres, by setting the v2 scope explicitly through the same {@code
 * set_config('app.current_tenants', ...)} channel the statement inspector reads - the same
 * mechanism {@code TenantScopedTransaction} uses, kept off the HTTP path only because HTTP already
 * carries its scope through {@code @Transactional} + {@code TxCtx}; a test is neither.
 *
 * <p>Every row is seeded with a raw native INSERT carrying an explicit tenant_id, never through v1
 * TenantContext.
 */
@Transactional
@TestPropertySource(
    properties =
        "openaev.tenant.active-tables=attackpath_execution_collector,attackpath_execution_remediation")
@WithMockUser(isAdmin = true)
@DisplayName(
    "attackpath_execution_collector / attackpath_execution_remediation read isolation at the"
        + " repository layer")
class AttackPathExecutionSnapshotIsolationTest extends IntegrationTest {

  @Autowired private AttackPathExecutionCollectorRepository collectorRepository;
  @Autowired private AttackPathExecutionRemediationRepository remediationRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  private String tenantA;
  private String tenantB;
  private String executionIdA;
  private String executionIdB;
  private String stepIdA;
  private String stepIdB;

  @BeforeEach
  void seedTwoTenantsWithOneRowEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("attackpath-snapshot-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("attackpath-snapshot-iso-b").getId();
    executionIdA = UUID.randomUUID().toString();
    executionIdB = UUID.randomUUID().toString();
    stepIdA = seedStep();
    stepIdB = seedStep();
    seedExecutionRow(tenantA, executionIdA);
    seedExecutionRow(tenantB, executionIdB);
    seedCollectorRow(tenantA, executionIdA);
    seedCollectorRow(tenantB, executionIdB);
    seedRemediationRow(tenantA, stepIdA);
    seedRemediationRow(tenantB, stepIdB);
  }

  @Test
  @DisplayName("scoped to tenant A: the collector snapshot query returns A's row and not B's")
  void collectorReadIsScopedToTenantA() {
    setScope(tenantA);

    List<io.openaev.database.model.attackpath.AttackPathExecutionCollector> ownRows =
        collectorRepository.findByExecutionIdAndTenantId(executionIdA, tenantA);
    assertEquals(1, ownRows.size(), "tenant A must see its own collector snapshot row");

    List<io.openaev.database.model.attackpath.AttackPathExecutionCollector> crossTenantRows =
        collectorRepository.findByExecutionIdAndTenantId(executionIdB, tenantA);
    assertTrue(
        crossTenantRows.isEmpty(),
        "under tenant A's scope, tenant B's collector snapshot row must not be visible even when"
            + " asked for by tenant A's own id argument (fail-closed, not a leak)");
  }

  @Test
  @DisplayName("scoped to tenant B: the collector snapshot query returns B's row and not A's")
  void collectorReadIsScopedToTenantB() {
    setScope(tenantB);

    List<io.openaev.database.model.attackpath.AttackPathExecutionCollector> ownRows =
        collectorRepository.findByExecutionIdAndTenantId(executionIdB, tenantB);
    assertEquals(1, ownRows.size(), "tenant B must see its own collector snapshot row");

    List<io.openaev.database.model.attackpath.AttackPathExecutionCollector> crossTenantRows =
        collectorRepository.findByExecutionIdAndTenantId(executionIdA, tenantB);
    assertTrue(
        crossTenantRows.isEmpty(),
        "under tenant B's scope, tenant A's collector snapshot row must not be visible");
  }

  @Test
  @DisplayName("scoped to tenant A: deleting by tenant B's execution id touches no row")
  void deleteAllByExecutionIdIsScopedToTenantA() {
    setScope(tenantA);
    collectorRepository.deleteAllByExecutionIdInAndTenantId(List.of(executionIdB), tenantA);
    entityManager.flush();
    entityManager.clear();

    assertEquals(
        1L,
        rawCollectorCount(executionIdB),
        "a delete scoped to tenant A must not remove tenant B's collector snapshot row");
  }

  @Test
  @DisplayName("scoped to tenant A: the remediation snapshot query returns A's row and not B's")
  void remediationReadIsScopedToTenantA() {
    setScope(tenantA);

    assertEquals(1, remediationRepository.findByStepId(stepIdA).size());
    assertTrue(
        remediationRepository.findByStepId(stepIdB).isEmpty(),
        "under tenant A's scope, tenant B's remediation snapshot row must not be visible");
  }

  @Test
  @DisplayName("scoped to tenant B: the remediation snapshot query returns B's row and not A's")
  void remediationReadIsScopedToTenantB() {
    setScope(tenantB);

    assertEquals(1, remediationRepository.findByStepId(stepIdB).size());
    assertTrue(
        remediationRepository.findByStepId(stepIdA).isEmpty(),
        "under tenant B's scope, tenant A's remediation snapshot row must not be visible");
  }

  /** Sets the v2 scope on the CURRENT (already-active) test transaction, tenant A or B only. */
  private void setScope(String tenantId) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", tenantId)
        .getSingleResult();
  }

  private long rawCollectorCount(String executionId) {
    return entityManager
        .unwrap(org.hibernate.Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT count(*) FROM attackpath_execution_collector WHERE"
                          + " attackpath_execution_id = ?")) {
                stmt.setString(1, executionId);
                try (var rows = stmt.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private void seedExecutionRow(String tenantId, String executionId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_execution (attackpath_execution_id,"
                + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
                + " attackpath_execution_target_kind, attackpath_execution_target_key,"
                + " attackpath_execution_executed_at, attackpath_execution_row_version, tenant_id)"
                + " VALUES (?1, ?2, 'AGENT', 'ASSET', ?3, now(), 0, ?4)")
        .setParameter(1, executionId)
        .setParameter(2, UUID.randomUUID().toString())
        .setParameter(3, "target-" + executionId)
        .setParameter(4, tenantId)
        .executeUpdate();
  }

  private void seedCollectorRow(String tenantId, String executionId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_execution_collector (attackpath_execution_collector_id,"
                + " attackpath_execution_collector_simulation_id, attackpath_execution_id,"
                + " attackpath_execution_collector_expectation_type,"
                + " attackpath_execution_collector_result_status_label, tenant_id) VALUES (?1,"
                + " ?2, ?3, 'DETECTION', 'SUCCESS', ?4)")
        .setParameter(1, UUID.randomUUID().toString())
        .setParameter(2, UUID.randomUUID().toString())
        .setParameter(3, executionId)
        .setParameter(4, tenantId)
        .executeUpdate();
  }

  private String seedStep() {
    Step step = StepFixture.getDefaultStepTemplate();
    StepComposer.Composer stepC = stepComposer.forStep(step);
    ExerciseComposer.Composer simComposer =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise());
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(simComposer)
        .withStep(stepC)
        .persist();
    return step.getId();
  }

  private void seedRemediationRow(String tenantId, String stepId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO attackpath_execution_remediation (attackpath_execution_remediation_id,"
                + " attackpath_execution_remediation_step_id, attackpath_execution_remediation_values,"
                + " attackpath_execution_remediation_author_rule,"
                + " attackpath_execution_remediation_security_platform, tenant_id) VALUES (?1, ?2,"
                + " 'values', 'HUMAN', ?3, ?4)")
        .setParameter(1, UUID.randomUUID().toString())
        .setParameter(2, stepId)
        .setParameter(3, UUID.randomUUID().toString())
        .setParameter(4, tenantId)
        .executeUpdate();
  }
}
