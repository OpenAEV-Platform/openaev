package io.openaev.aop;

import static io.openaev.rest.expectation.ExpectationApi.EXPECTATIONS_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.InjectExecutionCallbackService;
import io.openaev.service.chaining.ExternalUpdateEvent;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ExpectationFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.IOException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An update of a chained inject, or of one of its expectations, notifies the chaining step that
 * runs the inject whatever the scope of the caller that sends it. The step belongs to the tenant of
 * the simulation that owns the inject, which a request scoped to another tenant does not see once
 * the chaining tables are scoped by the statement inspector.
 *
 * <p>The simulation lives in a tenant of its own. Each call is sent on the non-prefixed route, from
 * the request thread's empty ambient tenant, by a caller who is a member of another tenant only, by
 * one with no membership at all, and by a member of the simulation's tenant: the step receives its
 * event in every case, stamped with the simulation's tenant.
 *
 * <p>The caller with no membership is the narrowest of the three, not an unscoped one: an empty set
 * of authorized tenants resolves to the missing scope ({@link
 * io.openaev.config.TenantScopeResolver#resolve}), whose setting value is empty, and {@code
 * can_access_tenant} denies every row on an empty setting. It found no step before the fix either,
 * so the three cases are three reproductions.
 *
 * <p>The production active tables are armed, so the class follows what production enforces.
 *
 * <p>The update queue is replaced by a mock so that the event the engine would consume is captured
 * as built, tenant included, and nothing reaches the broker.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: each request opens its own transaction,
 * as in production. The rows are committed in their tenant and removed with it.
 */
@WithMockUser(isAdmin = true)
@TestInstance(PER_CLASS)
@DisplayName("A chained inject update notifies its step in the simulation's tenant")
class WorkflowUpdateEventOutOfScopeCallersTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private record Seeded(String injectId, String expectationId, String stepId) {}

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private QueueChainingService queueChainingService;
  @Autowired private InjectExecutionCallbackService injectExecutionCallbackService;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;

  private String ownerTenantId;
  private String otherTenantId;
  private BatchQueueService<ExternalUpdateEvent> updateQueue;
  private Object originalUpdateQueue;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void createTheOwnerTenantAndCaptureTheUpdateQueue() throws Exception {
    exerciseComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    injectExpectationComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    workflowComposer.reset();
    stepComposer.reset();
    if (ownerTenantId == null) {
      ownerTenantId = tenantHelper.createTenant("chained-bridge-owner").getId();
    }
    // Creating a tenant enrols the user that created it, so the caller of whichever method happens
    // to create this one would be a member of the simulation's tenant. Each case states the
    // membership it wants, starting from none.
    tenantHelper.detachCurrentUserFromTenant(ownerTenantId);
    originalUpdateQueue = ReflectionTestUtils.getField(queueChainingService, "updateQueueService");
    updateQueue = mock(BatchQueueService.class);
    queueChainingService.setUpdateQueueService(updateQueue);
  }

  @AfterEach
  @SuppressWarnings("unchecked")
  void restoreTheUpdateQueue() {
    queueChainingService.setUpdateQueueService(
        (BatchQueueService<ExternalUpdateEvent>) originalUpdateQueue);
    TenantContext.clearCurrentTenant();
  }

  @AfterAll
  void deleteTheTenants() {
    tenantHelper.deleteCommittedTenants(ownerTenantId, otherTenantId);
  }

  @Nested
  @DisplayName("Expectation update from the UI")
  class ExpectationUpdate {

    @Test
    @DisplayName("given a scope without its tenant should notify the step in that tenant")
    void given_anExpectationUpdateFromAScopeWithoutItsTenant_should_notifyTheStep()
        throws Exception {
      // Arrange
      joinAnotherTenantOnly();
      Seeded seeded = seedChainedInject();

      // Act
      int httpStatus = updateExpectation(seeded.expectationId());

      // Assert
      assertStepNotifiedInItsTenant(httpStatus, seeded);
    }

    @Test
    @DisplayName("given no tenant membership should notify the step in that tenant")
    void given_anExpectationUpdateWithNoTenantMembership_should_notifyTheStep() throws Exception {
      // Arrange
      Seeded seeded = seedChainedInject();

      // Act
      int httpStatus = updateExpectation(seeded.expectationId());

      // Assert
      assertStepNotifiedInItsTenant(httpStatus, seeded);
    }

    @Test
    @DisplayName("given a membership of its tenant should notify the step in that tenant")
    void given_anExpectationUpdateFromAMemberOfItsTenant_should_notifyTheStep() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(ownerTenantId);
      Seeded seeded = seedChainedInject();

      // Act
      int httpStatus = updateExpectation(seeded.expectationId());

      // Assert
      assertStepNotifiedInItsTenant(httpStatus, seeded);
    }
  }

  @Nested
  @DisplayName("Inject execution callback")
  class InjectExecutionReport {

    private Object savedQueueService;

    @BeforeEach
    void forceTheSynchronousCallbackPath() {
      savedQueueService = injectExecutionCallbackService.getInjectTraceQueueService();
      injectExecutionCallbackService.setInjectTraceQueueService(null);
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void restoreTheAsynchronousCallbackPath() {
      injectExecutionCallbackService.setInjectTraceQueueService(
          (BatchQueueService<InjectExecutionCallback>) savedQueueService);
    }

    @Test
    @DisplayName("given a scope without its tenant should notify the step in that tenant")
    void given_anExecutionReportFromAScopeWithoutItsTenant_should_notifyTheStep() throws Exception {
      // Arrange
      joinAnotherTenantOnly();
      Seeded seeded = seedChainedInject();

      // Act
      int httpStatus = reportInjectExecution(seeded.injectId());

      // Assert
      assertStepNotifiedInItsTenant(httpStatus, seeded);
    }

    @Test
    @DisplayName("given a membership of its tenant should notify the step in that tenant")
    void given_anExecutionReportFromAMemberOfItsTenant_should_notifyTheStep() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(ownerTenantId);
      Seeded seeded = seedChainedInject();

      // Act
      int httpStatus = reportInjectExecution(seeded.injectId());

      // Assert
      assertStepNotifiedInItsTenant(httpStatus, seeded);
    }
  }

  // -- Act helpers, always on the non-prefixed route and from an empty ambient tenant --

  private int updateExpectation(String expectationId) throws Exception {
    clearTheAmbientTenant();
    return mvc.perform(
            put(EXPECTATIONS_URI + "/" + expectationId)
                .content(
                    asJsonString(ExpectationFixture.getExpectationUpdateInput("ui-source", 100.0)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private int reportInjectExecution(String injectId) throws Exception {
    clearTheAmbientTenant();
    InjectExecutionInput input = new InjectExecutionInput();
    input.setAction(InjectExecutionAction.command_execution);
    input.setStatus("SUCCESS");
    input.setMessage("whoami");
    input.setDuration(0);
    return mvc.perform(
            post(INJECT_URI + "/execution/callback/" + injectId)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private void clearTheAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  // -- Assertion shared by every caller --

  private void assertStepNotifiedInItsTenant(int httpStatus, Seeded seeded) throws IOException {
    assertThat(httpStatus).isEqualTo(200);
    ArgumentCaptor<ExternalUpdateEvent> events = ArgumentCaptor.forClass(ExternalUpdateEvent.class);
    verify(updateQueue, atLeastOnce()).publish(events.capture());
    assertThat(events.getAllValues())
        .extracting(ExternalUpdateEvent::getStepId, ExternalUpdateEvent::getTenantId)
        .as("the active step of the simulation's tenant, stamped with that tenant")
        .containsExactly(tuple(seeded.stepId(), ownerTenantId));
  }

  // -- Arrange helpers --

  /**
   * Makes the current caller a member of another tenant than the simulation's. The tenant is
   * created once, by the first caller that needs it.
   */
  private void joinAnotherTenantOnly() throws Exception {
    if (otherTenantId == null) {
      otherTenantId = tenantHelper.createTenant("chained-bridge-other").getId();
    }
    tenantHelper.attachCurrentUserToTenant(otherTenantId);
  }

  /**
   * Seeds, in the owner tenant, a simulation whose inject targets an agent and expects a detection
   * from it, and a running workflow whose step runs that inject.
   */
  private Seeded seedChainedInject() {
    return inTenant(
        ownerTenantId,
        () -> {
          BaseInjectExpectation expectation =
              InjectExpectationFixture.createDefaultDetectionInjectExpectation();
          AgentComposer.Composer agent =
              agentComposer.forAgent(AgentFixture.createDefaultAgentService());
          InjectStatus injectStatus = new InjectStatus();
          injectStatus.setName(ExecutionStatus.PENDING);
          InjectComposer.Composer inject =
              injectComposer
                  .forInject(InjectFixture.getDefaultInject())
                  .withInjectStatus(injectStatusComposer.forInjectStatus(injectStatus))
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
          return new Seeded(inject.get().getId(), expectation.getId(), step.get().getId());
        });
  }

  private <T> T inTenant(String tenant, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenant);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenant), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
