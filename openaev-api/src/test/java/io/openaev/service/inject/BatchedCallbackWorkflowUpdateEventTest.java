package io.openaev.service.inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.service.chaining.ExternalUpdateEvent;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * An inject execution callback consumed from the batch queue must notify the chaining step that
 * runs the inject, when the chaining tables are scoped by the statement inspector. The batch
 * handler runs outside any transaction and scopes each tenant's callbacks itself, so the step
 * lookup and the event's tenant must be read inside that tenant's scope.
 *
 * <p>The update queue is replaced by a mock so that the event the engine would consume is captured
 * as built, tenant included, and nothing reaches the broker.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the handler opens one top-level
 * transaction per tenant and refuses to run inside an active one. The rows are committed in their
 * own tenant and removed with it.
 */
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.tenant.active-tables=workflows,steps")
@DisplayName("A batched inject callback notifies the chaining step that runs the inject")
class BatchedCallbackWorkflowUpdateEventTest extends IntegrationTest {

  private record Seeded(String injectId, String agentId, String stepId) {}

  @Autowired private BatchingInjectStatusService batchingInjectStatusService;
  @Autowired private QueueChainingService queueChainingService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @MockitoSpyBean private StepService stepService;

  private BatchQueueService<ExternalUpdateEvent> updateQueue;
  private Object originalUpdateQueue;
  private String tenantA;
  private String tenantB;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void createTenantsAndCaptureTheUpdateQueue() throws Exception {
    exerciseComposer.reset();
    injectComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    workflowComposer.reset();
    stepComposer.reset();
    tenantA = tenantHelper.createTenantWithCurrentUser("batched-update-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("batched-update-b").getId();
    originalUpdateQueue = ReflectionTestUtils.getField(queueChainingService, "updateQueueService");
    updateQueue = mock(BatchQueueService.class);
    queueChainingService.setUpdateQueueService(updateQueue);
  }

  @AfterEach
  @SuppressWarnings("unchecked")
  void restoreTheUpdateQueueAndCleanup() {
    queueChainingService.setUpdateQueueService(
        (BatchQueueService<ExternalUpdateEvent>) originalUpdateQueue);
    for (String tenant : List.of(tenantA, tenantB)) {
      // workflows and steps are armed: the helper's delete on them joins this scoped transaction.
      inTenant(
          tenant,
          () -> {
            tenantHelper.deleteCommittedTenants(tenant);
            return null;
          });
    }
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Update event")
  class UpdateEvent {

    @Test
    @DisplayName(
        "given a batched callback for a chained inject, should notify the owning step under its"
            + " tenant")
    void given_batchedCallbackForChainedInject_should_notifyOwningStepUnderItsTenant()
        throws Exception {
      // -- Arrange --
      Seeded seeded = seedChainedInject(tenantA);
      // The batch worker carries no ambient tenant.
      TenantContext.clearCurrentTenant();

      // -- Act --
      batchingInjectStatusService.handleInjectExecutionCallback(List.of(callbackFor(seeded)));

      // -- Assert --
      assertThat(publishedEvents())
          .extracting(ExternalUpdateEvent::getStepId, ExternalUpdateEvent::getTenantId)
          .containsExactly(tuple(seeded.stepId(), tenantA));
    }

    @Test
    @DisplayName(
        "given a batch mixing two tenants, should notify each owning step under its own tenant")
    void given_batchMixingTwoTenants_should_notifyEachStepUnderItsOwnTenant() throws Exception {
      // -- Arrange --
      Seeded inA = seedChainedInject(tenantA);
      Seeded inB = seedChainedInject(tenantB);
      TenantContext.clearCurrentTenant();

      // -- Act --
      batchingInjectStatusService.handleInjectExecutionCallback(
          List.of(callbackFor(inA), callbackFor(inB)));

      // -- Assert --
      assertThat(publishedEvents())
          .extracting(ExternalUpdateEvent::getStepId, ExternalUpdateEvent::getTenantId)
          .containsExactlyInAnyOrder(tuple(inA.stepId(), tenantA), tuple(inB.stepId(), tenantB));
    }
  }

  @Nested
  @DisplayName("Retry and failure isolation")
  class RetryAndFailureIsolation {

    @Test
    @DisplayName(
        "given the queue down for a first batch, should replay its event under its own tenant on"
            + " a later batch of another tenant")
    void given_queueDownForFirstBatch_should_replayEventUnderItsTenantOnLaterBatch()
        throws Exception {
      // -- Arrange --
      Seeded inA = seedChainedInject(tenantA);
      Seeded inB = seedChainedInject(tenantB);
      TenantContext.clearCurrentTenant();
      // The first batch's send and its immediate replay both fail.
      doThrow(new IOException("queue down"))
          .doThrow(new IOException("queue down"))
          .doNothing()
          .when(updateQueue)
          .publish(any());
      batchingInjectStatusService.handleInjectExecutionCallback(List.of(callbackFor(inA)));

      // -- Act --
      batchingInjectStatusService.handleInjectExecutionCallback(List.of(callbackFor(inB)));

      // -- Assert --
      // The later batch, scoped to B only, sends B and then replays A, still stamped A.
      assertThat(publishedEvents())
          .extracting(ExternalUpdateEvent::getStepId, ExternalUpdateEvent::getTenantId)
          .containsExactly(
              tuple(inA.stepId(), tenantA),
              tuple(inA.stepId(), tenantA),
              tuple(inB.stepId(), tenantB),
              tuple(inA.stepId(), tenantA));
    }

    @Test
    @DisplayName(
        "given the step lookup failing for one tenant, should still process and notify the other")
    void given_stepLookupFailingForOneTenant_should_stillProcessAndNotifyTheOther()
        throws Exception {
      // -- Arrange --
      Seeded inA = seedChainedInject(tenantA);
      Seeded inB = seedChainedInject(tenantB);
      TenantContext.clearCurrentTenant();
      // Groups follow arrival order, so the first lookup is tenant A's.
      doThrow(new IllegalStateException("lookup failed"))
          .doCallRealMethod()
          .when(stepService)
          .findStepIdsActiveByInjectIds(anySet());

      // -- Act --
      List<InjectExecutionCallback> processed =
          batchingInjectStatusService.handleInjectExecutionCallback(
              List.of(callbackFor(inA), callbackFor(inB)));

      // -- Assert --
      assertThat(processed)
          .extracting(InjectExecutionCallback::getInjectId)
          .containsExactlyInAnyOrder(inA.injectId(), inB.injectId());
      assertThat(publishedEvents())
          .extracting(ExternalUpdateEvent::getStepId, ExternalUpdateEvent::getTenantId)
          .containsExactly(tuple(inB.stepId(), tenantB));
    }
  }

  // -- helpers --

  private List<ExternalUpdateEvent> publishedEvents() throws IOException {
    ArgumentCaptor<ExternalUpdateEvent> events = ArgumentCaptor.forClass(ExternalUpdateEvent.class);
    verify(updateQueue, atLeastOnce()).publish(events.capture());
    return events.getAllValues();
  }

  private InjectExecutionCallback callbackFor(Seeded seeded) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setAction(InjectExecutionAction.command_execution);
    input.setMessage("whoami");
    input.setStatus("SUCCESS");
    return InjectExecutionCallback.builder()
        .injectId(seeded.injectId())
        .agentId(seeded.agentId())
        .injectExecutionInput(input)
        .emissionDate(Instant.now().toEpochMilli())
        .build();
  }

  /**
   * Seeds, in the given tenant, a simulation whose inject targets an agent, and a running workflow
   * whose step runs that inject.
   */
  private Seeded seedChainedInject(String tenant) {
    return inTenant(
        tenant,
        () -> {
          AgentComposer.Composer agent =
              agentComposer.forAgent(AgentFixture.createDefaultAgentService());
          InjectComposer.Composer inject =
              injectComposer
                  .forInject(InjectFixture.getDefaultInject())
                  .withEndpoint(
                      endpointComposer
                          .forEndpoint(EndpointFixture.createEndpoint())
                          .withAgent(agent));
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
          return new Seeded(inject.get().getId(), agent.get().getId(), step.get().getId());
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
