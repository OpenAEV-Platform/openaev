package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecutionCollector;
import io.openaev.database.repository.attackpath.AttackPathExecutionCollectorRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRemediationRepository;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.DetectionRemediationFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.DetectionRemediationComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * attackpath_execution_collector / attackpath_execution_remediation read isolation (v2 activation,
 * no dedicated CRUD API for either table: both are read-only snapshot tables written by {@code
 * AttackPathExecutionIngestionService} and read by {@code AttackPathGraphService} and {@code
 * AttackPathSecurityPlatformResolver}).
 *
 * <p>Every row is written through the real service ({@code onRun} for the remediation snapshot,
 * {@code upsertExecutionCollectors} for the collector snapshot), the same entry points production
 * uses, not through a hand-seeded native INSERT: a test that inserts its own tenant_id can never
 * catch a missing or wrong stamp in the application's own write path. Reads go through {@link
 * TenantScopedTransaction#execute}, the same primitive a background caller uses, rather than a bare
 * {@code set_config} call: this class is deliberately not {@code @Transactional} (the primitive
 * refuses to open inside an active one, and {@code onRun}'s own {@code REQUIRES_NEW} commits
 * independently of any test transaction anyway), so cleanup below is explicit.
 */
@TestPropertySource(
    properties =
        "openaev.tenant.active-tables=attackpath_execution_collector,attackpath_execution_remediation")
@WithMockUser(isAdmin = true)
@DisplayName(
    "attackpath_execution_collector / attackpath_execution_remediation read isolation at the"
        + " repository layer")
class AttackPathExecutionSnapshotIsolationTest extends IntegrationTest {

  private static final String SIM_A = "SIM-SNAPSHOT-ISO-A";
  private static final String SIM_B = "SIM-SNAPSHOT-ISO-B";

  @Autowired private AttackPathExecutionIngestionService ingestionService;
  @Autowired private AttackPathExecutionCollectorRepository collectorRepository;
  @Autowired private AttackPathExecutionRemediationRepository remediationRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DetectionRemediationComposer detectionRemediationComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;

  private JdbcTemplate jdbc;
  private Tenant tenantA;
  private Tenant tenantB;
  private String executionIdA;
  private String executionIdB;
  private String stepIdA;
  private String stepIdB;

  @BeforeEach
  void seedTwoTenantsThroughTheRealWritePath() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = tenantHelper.createTenantWithCurrentUser("attackpath-snapshot-iso-a");
    tenantB = tenantHelper.createTenantWithCurrentUser("attackpath-snapshot-iso-b");
    TenantContext.clearCurrentTenant();

    SeededRun runA = seedRun(tenantA, SIM_A, "iso-a");
    SeededRun runB = seedRun(tenantB, SIM_B, "iso-b");
    executionIdA = runA.executionId;
    executionIdB = runB.executionId;
    stepIdA = runA.stepId;
    stepIdB = runB.stepId;
  }

  @AfterEach
  void cleanUp() {
    jdbc.update(
        "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id IN (?, ?)",
        SIM_A,
        SIM_B);
    // Onboarding a tenant provisions its own default collector, referencing a per-tenant
    // collector_type row with no cascading FK: deleteCommittedTenants only clears collector_types,
    // so the collector referencing it must go first or the tenant delete fails on that FK.
    jdbc.update(
        "DELETE FROM collectors WHERE tenant_id IN (?, ?)", tenantA.getId(), tenantB.getId());
    tenantHelper.deleteCommittedTenants(tenantA.getId());
    tenantHelper.deleteCommittedTenants(tenantB.getId());
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("scoped to tenant A: the collector snapshot query returns A's row and not B's")
  void collectorReadIsScopedToTenantA() {
    List<AttackPathExecutionCollector> ownRows =
        scopedRead(
            tenantA.getId(),
            () -> collectorRepository.findByExecutionIdAndTenantId(executionIdA, tenantA.getId()));
    assertThat(ownRows).as("tenant A must see its own collector snapshot row").hasSize(1);

    List<AttackPathExecutionCollector> crossTenantRows =
        scopedRead(
            tenantA.getId(),
            () -> collectorRepository.findByExecutionIdAndTenantId(executionIdB, tenantA.getId()));
    assertThat(crossTenantRows)
        .as(
            "under tenant A's scope, tenant B's collector snapshot row must not be visible even"
                + " when asked for by tenant A's own id argument (fail-closed, not a leak)")
        .isEmpty();
  }

  @Test
  @DisplayName("scoped to tenant B: the collector snapshot query returns B's row and not A's")
  void collectorReadIsScopedToTenantB() {
    List<AttackPathExecutionCollector> ownRows =
        scopedRead(
            tenantB.getId(),
            () -> collectorRepository.findByExecutionIdAndTenantId(executionIdB, tenantB.getId()));
    assertThat(ownRows).as("tenant B must see its own collector snapshot row").hasSize(1);

    List<AttackPathExecutionCollector> crossTenantRows =
        scopedRead(
            tenantB.getId(),
            () -> collectorRepository.findByExecutionIdAndTenantId(executionIdA, tenantB.getId()));
    assertThat(crossTenantRows)
        .as("under tenant B's scope, tenant A's collector snapshot row must not be visible")
        .isEmpty();
  }

  @Test
  @DisplayName("scoped to tenant A: deleting by tenant B's execution id touches no row")
  void deleteAllByExecutionIdIsScopedToTenantA() {
    tenantTx.execute(
        TxCtx.forTenant(tenantA.getId()),
        () ->
            collectorRepository.deleteAllByExecutionIdInAndTenantId(
                List.of(executionIdB), tenantA.getId()));

    assertThat(rawCollectorCount(executionIdB))
        .as("a delete scoped to tenant A must not remove tenant B's collector snapshot row")
        .isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "scoped to tenant A: tenant B's collector row stays hidden even when B's own tenant id is"
          + " passed as the argument")
  void given_tenantAScope_should_notReadCollectorRowOfTenantBAskedForWithTenantBId() {
    // Arrange - the positive case first, so an empty table cannot pass for a filtered one.
    assertThat(
            scopedRead(
                tenantA.getId(),
                () ->
                    collectorRepository.findByExecutionIdAndTenantId(
                        executionIdA, tenantA.getId())))
        .as("tenant A must see its own collector snapshot row under its own scope")
        .hasSize(1);

    // Act - the query's tenant argument names tenant B, so the explicit predicate matches B's row
    // and only the v2 scope can hide it. The sibling tests above pass tenant A's id for B's
    // execution, which the predicate alone rejects, so they hold with the table de-activated.
    List<AttackPathExecutionCollector> crossTenantRows =
        scopedRead(
            tenantA.getId(),
            () -> collectorRepository.findByExecutionIdAndTenantId(executionIdB, tenantB.getId()));

    // Assert
    assertThat(crossTenantRows)
        .as(
            "under tenant A's scope, tenant B's collector snapshot row must not be returned even"
                + " when the caller supplies tenant B's id in the query itself")
        .isEmpty();
  }

  @Test
  @DisplayName(
      "scoped to tenant A: deleting tenant B's collector row with B's own tenant id removes"
          + " nothing")
  void given_tenantAScope_should_notDeleteCollectorRowOfTenantBAskedForWithTenantBId() {
    // Arrange
    assertThat(rawCollectorCount(executionIdB))
        .as("tenant B's collector snapshot row exists before the scoped delete")
        .isEqualTo(1L);

    // Act - same shape as the read above: the statement's own predicate names tenant B, so the
    // scope is the only thing that can keep the row.
    tenantTx.execute(
        TxCtx.forTenant(tenantA.getId()),
        () ->
            collectorRepository.deleteAllByExecutionIdInAndTenantId(
                List.of(executionIdB), tenantB.getId()));

    // Assert
    assertThat(rawCollectorCount(executionIdB))
        .as(
            "a delete issued under tenant A's scope must not remove tenant B's collector snapshot"
                + " row, even when it names tenant B explicitly")
        .isEqualTo(1L);
  }

  @Test
  @DisplayName("with no scope at all: the collector snapshot read fails closed")
  void given_noScopeSet_should_failClosedOnTheCollectorSnapshotRead() {
    // Act & Assert - the control for the two tests above, on a different line than the
    // active-tables property: those reads return rows BECAUSE a scope is set.
    assertThat(collectorRepository.findByExecutionIdAndTenantId(executionIdA, tenantA.getId()))
        .as("an active-table read with no tenant scope must return nothing")
        .isEmpty();
  }

  @Test
  @DisplayName("scoped to tenant A: the remediation snapshot query returns A's row and not B's")
  void remediationReadIsScopedToTenantA() {
    assertThat(scopedRead(tenantA.getId(), () -> remediationRepository.findByStepId(stepIdA)))
        .hasSize(1);
    assertThat(scopedRead(tenantA.getId(), () -> remediationRepository.findByStepId(stepIdB)))
        .as("under tenant A's scope, tenant B's remediation snapshot row must not be visible")
        .isEmpty();
  }

  @Test
  @DisplayName("scoped to tenant B: the remediation snapshot query returns B's row and not A's")
  void remediationReadIsScopedToTenantB() {
    assertThat(scopedRead(tenantB.getId(), () -> remediationRepository.findByStepId(stepIdB)))
        .hasSize(1);
    assertThat(scopedRead(tenantB.getId(), () -> remediationRepository.findByStepId(stepIdA)))
        .as("under tenant B's scope, tenant A's remediation snapshot row must not be visible")
        .isEmpty();
  }

  /** Opens the tenant's own top-level scoped transaction to run one read, then closes it. */
  private <T> T scopedRead(String tenantId, Supplier<T> read) {
    return tenantTx.execute(TxCtx.forTenant(tenantId), read);
  }

  private long rawCollectorCount(String executionId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM attackpath_execution_collector WHERE"
                + " attackpath_execution_id = ?",
            Long.class,
            executionId);
    return count == null ? 0L : count;
  }

  /**
   * Reproduces the executor's shape: {@code onRun} and {@code upsertExecutionCollectors} both open
   * their own {@code REQUIRES_NEW} transaction, which requires an active ambient one to nest from.
   */
  private void runAsTheExecutorWould(Runnable hook) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> hook.run());
  }

  private record SeededRun(String executionId, String stepId) {}

  /**
   * Builds one endpoint/agent/payload/remediation graph under the given tenant and drives both
   * production write paths against it: {@code onRun} for the execution + remediation snapshot rows,
   * {@code upsertExecutionCollectors} for the collector snapshot row. Returns the ids the isolation
   * assertions read back.
   */
  private SeededRun seedRun(Tenant tenant, String simulationId, String suffixPrefix) {
    // A fresh id every call, not just per-tenant: two isolation tests in the same class each run
    // their own @BeforeEach, and a fixed id would collide with the previous test's row if its
    // @AfterEach cleanup ever lagged, causing a flaky duplicate-key failure unrelated to isolation.
    String suffix = suffixPrefix + "-" + UUID.randomUUID();
    TenantContext.setCurrentTenant(tenant.getId());

    Endpoint endpoint = EndpointFixture.createEndpoint("corp-dc-" + suffix);
    endpoint.setHostname("corp-dc-" + suffix);
    endpoint.setIps(new String[] {"10.0.0.5"});
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    endpoint.setTenant(tenant);

    Agent agent =
        AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor(tenant.getId()));
    agent.setId("agt-" + suffix);
    agent.setAsset(endpoint);
    agent.setExecutedByUser("agent-" + suffix);
    endpointComposer.forEndpoint(endpoint).withAgent(agentComposer.forAgent(agent)).persist();

    var remediation = DetectionRemediationFixture.createDefaultDetectionRemediation();
    remediation.setValues("remediation values " + suffix);
    Payload payload =
        payloadComposer
            .forPayload(PayloadFixture.createDefaultCommand())
            .withDetectionRemediation(
                detectionRemediationComposer
                    .forDetectionRemediation(remediation)
                    .withSecurityPlatform(
                        securityPlatformComposer.forSecurityPlatform(
                            SecurityPlatformFixture.createDefault(
                                "EDR platform " + suffix, "EDR"))))
            .persist()
            .get();

    Exercise exercise = new Exercise();
    exercise.setId(simulationId);

    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    contract.setNeedsExecutor(true);
    contract.setPayload(payload);

    Inject inject = InjectFixture.getDefaultInject();
    inject.setId("exec-" + suffix);
    inject.setExercise(exercise);
    inject.setTenant(tenant);
    inject.setTitle("payload-" + suffix);
    inject.setInjectorContract(contract);
    inject.setAssets(List.of(endpoint));

    // The execution row freezes step.getStepTemplate().getId(), so a step with no template
    // triggers a NullPointerException inside onRun: link one, as production always has.
    StepComposer.Composer templateComposer =
        stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    Step step = StepFixture.getDefaultStepTemplate();
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(templateComposer)
        .withStep(stepComposer.forStep(step).withStepTemplate(templateComposer))
        .persist();

    TenantContext.clearCurrentTenant();

    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step, "cme"));

    PreventionInjectExpectation prevention = new PreventionInjectExpectation();
    prevention.setAgent(agent);
    InjectExpectationResult result = new InjectExpectationResult();
    result.setSourceName("EDR platform " + suffix);
    result.setResult("Prevented");
    result.setDate("2026-09-01T10:00:00Z");
    prevention.setResults(List.of(result));
    runAsTheExecutorWould(
        () -> ingestionService.upsertExecutionCollectors(inject, List.of(prevention)));

    String executionId =
        AttackPathIds.executionNode("exec-" + suffix, endpoint.getId(), agent.getId());
    return new SeededRun(executionId, step.getId());
  }
}
