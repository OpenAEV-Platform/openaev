package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetAgentJob;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
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
 * Pins how {@link WorkflowEndService#deleteAllAssetAgentJobsBySimulationIds} stays correctly scoped
 * once {@code asset_agent_jobs} is v2-active, on all three real causes that reach it (TIMEOUT via
 * {@code WorkflowTimeoutJob}/{@code AutonomousTimeoutJob}, CANCELED via {@code ExerciseApi} or the
 * autonomous-run watchdog, NO_MORE_PROGRESS via {@code StepEventService}/ {@code
 * AutonomousRunApi#evaluateAttackPath}). None of them route through {@code QueueChainingJob}, which
 * an earlier, incorrect inventory had claimed (see the activation report).
 *
 * <p>The delete's own {@code @Query} already carries an explicit {@code tenant_id = :tenantId}
 * predicate (bind parameter, not the {@code TenantStatementInspector} rewrite), so its correctness
 * never depended on the v2 GUC. It DID depend on where that {@code tenantId} bind value came from:
 * {@link WorkflowEndService#manageWorkflowEnd} used to read it from the ambient v1 {@code
 * TenantContext} - correct on the prefixed {@code /api/tenants/{id}/...} route ({@code
 * TenantInterceptor} sets it from the path) but WRONG on the legacy {@code X-Tenant-Ids} header
 * route, which {@code TenantInterceptor} never touches: {@code TenantContext.getCurrentTenant()}
 * falls back to the default tenant there, so a CANCELED end for any non-default tenant deleted zero
 * rows, silently, exactly the D29/I4 header-route gap this activation must close. The fix (this
 * activation) reads the tenant from the already-loaded simulation instead, which is correct on both
 * routes by construction - this test pins that shape directly: the right tenant deletes, any other
 * tenant id does not.
 *
 * <p>NOT {@code @Transactional}: {@link TenantScopedTransaction#execute} refuses to open inside an
 * already-active transaction (see its javadoc; {@code AssetGroupBackgroundIsolationTest} is the
 * reference pattern this follows). Seeding sets the ambient v1 {@link TenantContext} explicitly and
 * uses the ordinary composers, exactly like {@code IntegrationTest}'s own base setup; cleanup goes
 * through an auto-committing {@link JdbcTemplate}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=asset_agent_jobs")
@WithMockUser(isAdmin = true)
@DisplayName("asset_agent_jobs deletion (WorkflowEndService) tenant scope")
class AssetAgentJobDeletionScopeTest extends IntegrationTest {

  @Autowired private WorkflowEndService workflowEndService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private AssetAgentJobRepository assetAgentJobRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String otherTenantId;
  private String simulationId;
  private String assetAgentJobId;

  @BeforeEach
  void seedOneTenantWithOneAssetAgentJob() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    otherTenantId = UUID.randomUUID().toString();
    for (String id : new String[] {tenantId, otherTenantId}) {
      jdbc.update(
          "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
              + " VALUES (?, ?, now(), now())",
          id,
          "asset-agent-job-scope-" + id);
    }

    TenantContext.setCurrentTenant(tenantId);
    try {
      Exercise simulation =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
      simulationId = simulation.getId();
      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExercise(exerciseComposer.forExercise(simulation))
              .persist()
              .get();
      Endpoint endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();
      Agent agent =
          agentComposer
              .forAgent(AgentFixture.createAgent(endpoint, "scope-test-" + UUID.randomUUID()))
              .persist()
              .get();

      AssetAgentJob job = new AssetAgentJob();
      job.setCommand("whoami");
      job.setAgent(agent);
      job.setInject(inject);
      job.setTenant(simulation.getTenant());
      job.setCreatedAt(Instant.now());
      assetAgentJobId = assetAgentJobRepository.save(job).getId();
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM asset_agent_jobs WHERE tenant_id IN (?, ?)", tenantId, otherTenantId);
    jdbc.update("DELETE FROM agents WHERE tenant_id IN (?, ?)", tenantId, otherTenantId);
    jdbc.update("DELETE FROM assets WHERE tenant_id IN (?, ?)", tenantId, otherTenantId);
    jdbc.update("DELETE FROM injects WHERE tenant_id IN (?, ?)", tenantId, otherTenantId);
    jdbc.update("DELETE FROM exercises WHERE tenant_id IN (?, ?)", tenantId, otherTenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantId, otherTenantId);
  }

  /**
   * Ground truth via raw JDBC, deliberately not the repository: with the table v2-active, an
   * unscoped {@code findById} outside {@code tenantTx.execute(...)} fails closed to empty
   * regardless of what is actually in the row, which would make an assertion on it prove nothing
   * (see the activate-tenant-table skill's {@code FindingApiTest} {@code [] == []} trap). Native
   * SQL never goes through {@code TenantStatementInspector}.
   */
  private boolean rowStillExists() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM asset_agent_jobs WHERE asset_agent_id = ?",
            Integer.class,
            assetAgentJobId);
    return count != null && count > 0;
  }

  @Test
  @DisplayName(
      "given the simulation's own tenant - what manageWorkflowEnd now passes - the delete removes the row")
  void givenTheSimulationsOwnTenant_deleteRemovesTheRow() {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () ->
            workflowEndService.deleteAllAssetAgentJobsBySimulationIds(
                simulationId, tenantId, WorkflowEndService.WORKFLOW_END_CAUSE.CANCELED));

    assertTrue(
        !rowStillExists(),
        "the simulation's own tenant id must be enough for the delete to actually remove the row");
  }

  @Test
  @DisplayName(
      "given any OTHER tenant id - what the pre-fix TenantContext fallback produced on the header"
          + " route - the delete removes NOTHING, silently")
  void givenAnyOtherTenantId_deleteRemovesNothing() {
    tenantTx.execute(
        TxCtx.forTenant(otherTenantId),
        () ->
            workflowEndService.deleteAllAssetAgentJobsBySimulationIds(
                simulationId, otherTenantId, WorkflowEndService.WORKFLOW_END_CAUSE.CANCELED));

    assertTrue(
        rowStillExists(),
        "a tenant id other than the simulation's own must fail closed (delete nothing) - this is"
            + " exactly the shape TenantContext.getCurrentTenant()'s default-tenant fallback"
            + " produced on the X-Tenant-Ids header route before manageWorkflowEnd read the"
            + " simulation's own tenant instead");
  }
}
