package io.openaev.aop;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;
import static io.openaev.rest.expectation.ExpectationApi.INJECTS_EXPECTATIONS_URI;
import static io.openaev.rest.expectation.ExpectationApi.TENANT_INJECTS_EXPECTATIONS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.collectors.expectations_expiration_manager.ExpectationsExpirationManagerJob;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.rest.inject.form.InjectExpectationUpdateFromCollectorInput;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An update of a chained inject's expectation must notify the chaining step that runs the inject,
 * when the chaining tables are scoped by the statement inspector. The update-event aspect looks the
 * step up inside the caller's transaction, so it sees the step only when that transaction already
 * carries the inject's tenant: the collector endpoint through its {@code TxCtx}, the expiration job
 * through its per-tenant transaction.
 *
 * <p>{@code collectors} is armed with the chaining tables because it is active in production and
 * both callers resolve their collector by id alone, relying on the inspector to pick the row of the
 * transaction's tenant.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the job opens one top-level transaction
 * per tenant and refuses to run inside an active one, and each request must open its own
 * transaction, as in production. The rows are committed in their own tenant and removed with it.
 */
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.tenant.active-tables=collectors,workflows,steps")
@DisplayName("A scoped expectation update notifies the chaining step that runs the inject")
class WorkflowUpdateEventScopedCallersTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  private record Seeded(String expectationId, String stepId) {}

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ExpectationsExpirationManagerJob expectationsExpirationManagerJob;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @MockitoSpyBean private QueueChainingService queueChainingService;

  private String tenantId;

  @BeforeEach
  void createTenantAndSilenceThePublisher() throws Exception {
    exerciseComposer.reset();
    injectComposer.reset();
    injectExpectationComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    workflowComposer.reset();
    stepComposer.reset();
    // The caller is a member of this tenant only, and onboarding registers the built-in
    // expiration collector in it.
    tenantId = tenantHelper.createTenantWithCurrentUser("update-event").getId();
    doNothing().when(queueChainingService).updateStep(anyString());
  }

  @AfterEach
  void cleanup() {
    // collectors is armed: the helper's delete on it joins this scoped transaction.
    inTenant(
        tenantId,
        () -> {
          tenantHelper.deleteCommittedTenants(tenantId);
          return null;
        });
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Collector update through the HTTP endpoint")
  class CollectorUpdate {

    private Seeded seeded;

    @BeforeEach
    void seedAChainedInjectWithAPendingAgentExpectation() {
      seeded =
          seedChainedInject(InjectExpectationFixture.createDefaultDetectionInjectExpectation());
    }

    @Test
    @DisplayName("given the tenant-prefixed route, should notify the owning step")
    void given_tenantPrefixedRoute_should_notifyOwningStep() throws Exception {
      // -- Act --
      mvc.perform(
              put(
                      TENANT_INJECTS_EXPECTATIONS_URI + "/{expectationId}",
                      tenantId,
                      seeded.expectationId())
                  .content(asJsonString(collectorVerdict()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      verify(queueChainingService).updateStep(seeded.stepId());
    }

    @Test
    @DisplayName("given the non-prefixed route with a tenant header, should notify the owning step")
    void given_nonPrefixedRouteWithTenantHeader_should_notifyOwningStep() throws Exception {
      // -- Arrange --
      // No path tenant, so nothing sets the thread-local: start from production's empty one.
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // -- Act --
      mvc.perform(
              put(INJECTS_EXPECTATIONS_URI + "/{expectationId}", seeded.expectationId())
                  .header(TENANT_IDS_HEADER, tenantId)
                  .content(asJsonString(collectorVerdict()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      verify(queueChainingService).updateStep(seeded.stepId());
    }

    @Test
    @DisplayName(
        "given the non-prefixed route without a header, as a collector calls it,"
            + " should notify the owning step")
    void given_nonPrefixedRouteWithoutHeader_should_notifyOwningStep() throws Exception {
      // -- Arrange --
      // The scope is then the caller's memberships, which is this tenant only.
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // -- Act --
      mvc.perform(
              put(INJECTS_EXPECTATIONS_URI + "/{expectationId}", seeded.expectationId())
                  .content(asJsonString(collectorVerdict()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      verify(queueChainingService).updateStep(seeded.stepId());
    }

    private InjectExpectationUpdateFromCollectorInput collectorVerdict() {
      return InjectExpectationFixture.getInjectExpectationUpdateInput(
          COLLECTOR_ID, "Detected", true);
    }
  }

  @Nested
  @DisplayName("Expiration job")
  class ExpirationJob {

    @Test
    @DisplayName("given an expired agent expectation, the job should notify the owning step")
    void given_expiredAgentExpectation_should_notifyOwningStep() throws Exception {
      // -- Arrange --
      Seeded seeded =
          seedChainedInject(InjectExpectationFixture.createDefaultDetectionInjectExpectation());
      backdatePastItsExpiration(seeded.expectationId());
      // The job resolves the built-in collector in every tenant it visits. The default tenant
      // registers it lazily, on its first manager start, so register it here as that start would.
      registerExpirationCollector(Tenant.DEFAULT_TENANT_UUID);
      TenantContext.clearCurrentTenant();

      // -- Act --
      expectationsExpirationManagerJob.run();

      // -- Assert --
      verify(queueChainingService).updateStep(seeded.stepId());
    }

    // created_at is a creation timestamp, so it can only be moved back once the row exists.
    private void backdatePastItsExpiration(String expectationId) {
      int updated =
          inTenant(
              tenantId,
              () ->
                  entityManager
                      .createNativeQuery(
                          "UPDATE injects_expectations SET inject_expectation_created_at = :past"
                              + " WHERE inject_expectation_id = :id")
                      .setParameter("past", Instant.now().minus(1, ChronoUnit.DAYS))
                      .setParameter("id", expectationId)
                      .executeUpdate());
      assertThat(updated).isEqualTo(1);
    }

    private void registerExpirationCollector(String tenant) {
      inTenant(
          tenant,
          () -> {
            try {
              expectationsExpirationManagerJob.registerForTenant(tenant);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
            return null;
          });
    }
  }

  // -- helpers --

  /**
   * Seeds, in the test tenant, a simulation whose inject targets an agent and expects a detection
   * from it, and a running workflow whose step runs that inject.
   */
  private Seeded seedChainedInject(BaseInjectExpectation expectation) {
    return inTenant(
        tenantId,
        () -> {
          AgentComposer.Composer agent =
              agentComposer.forAgent(AgentFixture.createDefaultAgentService());
          InjectComposer.Composer inject =
              injectComposer
                  .forInject(InjectFixture.getDefaultInject())
                  .withEndpoint(
                      endpointComposer
                          .forEndpoint(EndpointFixture.createEndpoint())
                          .withAgent(agent))
                  .withExpectation(
                      injectExpectationComposer.forExpectation(expectation).withAgent(agent));
          ExerciseComposer.Composer simulation =
              exerciseComposer
                  .forExercise(ExerciseFixture.createDefaultExercise())
                  .withInject(inject)
                  .persist();
          StepComposer.Composer step =
              stepComposer.forStep(
                  StepFixture.getInjectExecutionStep(StepStatus.RUN, inject.get().getId()));
          // The simulation is already persisted above: the step needs the inject id it assigns.
          Workflow workflow = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
          workflow.setSimulation(simulation.get());
          workflowComposer.forWorkflow(workflow).withStep(step).persist();
          return new Seeded(expectation.getId(), step.get().getId());
        });
  }

  private <T> T inTenant(String tenant, Supplier<T> work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenant);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenant), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }
}
