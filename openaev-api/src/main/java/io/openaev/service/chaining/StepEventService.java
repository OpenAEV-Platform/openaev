package io.openaev.service.chaining;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import io.openaev.api.chaining.ActionStep;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles step lifecycle events consumed from the chaining queues: ready events (execute a step)
 * and external update events (propagate external results back into the workflow).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StepEventService implements StepEventHandler, ExternalUpdateEventHandler {

  private final ChainingConfig chainingConfig;
  private final StepService stepService;
  private final WorkflowService workflowService;
  private final StepRepository stepRepository;
  private final QueueChainingService queueChainingService;

  /**
   * MT v2 background transaction primitive (activate-tenant-table skill, Phase 5b). The chaining
   * queue consumers run on worker threads that carry no tenant scope; they open a per-event
   * tenant-scoped transaction through this primitive rather than {@code @Transactional} or a raw
   * {@code TransactionTemplate}. This sets the v2 GUC ({@code app.current_tenants}), scoping the
   * tenant-active tables the run writes (e.g. {@code attackpath_execution}).
   *
   * <p>The GUC alone is not enough here: a step run also reads v1 {@code @Filter} entities that are
   * not yet migrated to v2 (injector contract, endpoints, assets). Those are scoped by the
   * thread-local {@link TenantContext} via {@link io.openaev.aop.HibernateFilterTransactionAspect},
   * not by the GUC. Each consumer therefore also sets {@link TenantContext} to the run's tenant for
   * the duration of the event (set-then-finally-clear on the pooled worker thread), mirroring the
   * background scheduler jobs (e.g. {@code ScenarioExecutionJob}). Both scopes carry the same
   * tenant and cohabit: the primitive covers v2, {@link TenantContext} covers v1.
   */
  private final TenantScopedTransaction tenantTx;

  // -- READY EVENTS --

  /**
   * Consume ready events from queue.
   *
   * @param events list of events
   * @return consumed list of events
   */
  public List<StepEvent> handleReadyEvent(List<StepEvent> events) {
    events.forEach(this::handleReadyStepEvent);
    return events;
  }

  /**
   * Handle ready event and run the corresponding step.
   *
   * @param stepEvent event to handle
   */
  @Override
  public void handleReadyStepEvent(StepEvent stepEvent) {
    // #6357: the chaining worker thread carries no tenant scope. Scope the event to its run's
    // tenant
    // on both mechanisms so run() resolves the right rows: the MT v2 primitive sets the GUC for the
    // tenant-active tables the run writes (attackpath_execution), and TenantContext scopes the v1
    // @Filter entities the run reads (injector contract, endpoints, assets) that are not yet
    // migrated to v2. TenantContext is set on the pooled worker thread and cleared in finally so it
    // never leaks to the next event (mirrors ScenarioExecutionJob). A null tenantId
    // (legacy/in-flight
    // message) falls back to the default tenant, today's behaviour. Per-event scoping keeps a
    // failing
    // event from rolling the rest of the batch back.
    String tenantId =
        stepEvent.getTenantId() != null ? stepEvent.getTenantId() : DEFAULT_TENANT_UUID;
    TenantContext.setCurrentTenant(tenantId);
    try {
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () ->
              stepRepository
                  .findById(stepEvent.getStepId())
                  .ifPresentOrElse(
                      this::run,
                      () ->
                          log.error(
                              "[Chaining] Ready consume: Step not found for StepEvent ID: {}",
                              stepEvent.getStepId())));
    } catch (Exception e) {
      if (stepEvent.getRetryCount() < chainingConfig.getMaxRetryCount()) {
        stepEvent.setRetryCount(stepEvent.getRetryCount() + 1);
        log.warn(
            "[Chaining] Transaction failed for StepEvent {}. Re-queuing (retry {}/{}).",
            stepEvent.getStepId(),
            stepEvent.getRetryCount(),
            chainingConfig.getMaxRetryCount(),
            e);
        try {
          queueChainingService.republishReadyEvent(stepEvent);
        } catch (IOException ioEx) {
          log.error(
              "[Chaining] Failed to re-queue StepEvent {} after transactional failure. Event is lost.",
              stepEvent.getStepId(),
              ioEx);
        }
      } else {
        log.error(
            "[Chaining] Transaction failed for StepEvent {} after {} retries. Event is dropped.",
            stepEvent.getStepId(),
            chainingConfig.getMaxRetryCount(),
            e);
      }
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  /**
   * Run step that is ready.
   *
   * @param stepReady step ready to run
   */
  void run(Step stepReady) {
    // Guard: ignore if workflow run has already ended (e.g. timeout).
    // Reads fresh status from DB to catch concurrent timeout completion.
    Workflow workflowRun = stepReady.getWorkflow();
    if (workflowRun != null && workflowService.isWorkflowEnded(workflowRun.getId())) {
      log.info(
          "[Chaining] Ignoring run request for step {} because workflow run {} has ended.",
          stepReady.getId(),
          workflowRun.getId());
      return;
    }

    Step stepRun;
    try {
      ActionStep actionStep =
          stepService.factoryAction(stepReady.getStepAction(), stepReady.getId());
      stepRun =
          actionStep
              .run(stepReady)
              .orElseThrow(() -> new ChainingException("Step (READY) execution failed"));
    } catch (ChainingException e) {
      // todo system notif queue fail + system log for step + status FAIL
      log.error(
          "[Chaining] Ready consume : Step (READY) execution failed. Step moved to (END) state. Step ID: {} {}",
          stepReady.getId(),
          e.getMessage(),
          e);
      stepReady.setStatus(StepStatus.END);
      stepService.saveStep(stepReady);
      return;
      // todo Check all executed steps, if all ended, end workflow run
      /* int runningStep = stepRepository.countRunningStep(stepReady.getWorkflow().getId());
      if (runningStep == 0) {
        // TODO manage steptemplate with time delay
        Workflow run = stepReady.getWorkflow();
        run.setStatus(WorkflowStatus.END);
        workflowService.saveWorkflowRun(run);
      }*/
    }

    stepRun.setStatus(StepStatus.RUN);
    stepService.saveStep(stepRun);
  }

  // -- EXTERNAL UPDATE EVENTS --

  /**
   * Consume update events from queue.
   *
   * @param events list of events
   * @return consumed list of events
   */
  // #6357: NOT @Transactional. A batch-level transaction would fire
  // HibernateFilterTransactionAspect
  // once with the (context-less) default tenant and pin the tenantFilter to it for every event in
  // the
  // batch, defeating the per-event tenant restore below. Each event runs in its own transaction
  // with
  // its tenant set first (mirrors handleReadyStepEvent), which also isolates a failing event from
  // the
  // rest of the batch.
  public List<ExternalUpdateEvent> handleExternalUpdateEvent(List<ExternalUpdateEvent> events) {
    events.forEach(this::handleExternalUpdateEvent);
    return events;
  }

  /**
   * Handle external update event and create next ready step.
   *
   * @param stepEvent event to handle
   */
  @Override
  public void handleExternalUpdateEvent(ExternalUpdateEvent stepEvent) {
    // #6357: same dual scoping as the ready path (see handleReadyStepEvent) - the MT v2 primitive
    // for the tenant-active writes, TenantContext for the v1 @Filter reads, both on the run's
    // tenant, set-then-finally-clear on the pooled worker thread. Null tenantId (legacy/in-flight
    // message) falls back to the default tenant.
    String tenantId =
        stepEvent.getTenantId() != null ? stepEvent.getTenantId() : DEFAULT_TENANT_UUID;
    TenantContext.setCurrentTenant(tenantId);
    try {
      tenantTx.execute(TxCtx.forTenant(tenantId), () -> processExternalUpdateEvent(stepEvent));
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private void processExternalUpdateEvent(ExternalUpdateEvent stepEvent) {
    Step stepRun;
    try {
      stepRun = stepService.findByIdAndStatus(stepEvent.getStepId(), StepStatus.RUN);
    } catch (ElementNotFoundException e) {
      // Todo: system notif queue fail + system log for step + status FAIL
      log.error(
          "[Chaining] Update consume: Step (RUN) not found. Step ID: {} {}",
          stepEvent.getStepId(),
          e.getMessage(),
          e);
      return;
    }

    // Guard: ignore if workflow run has already ended (e.g. timeout).
    // Reads fresh status from DB to catch concurrent timeout completion.
    Workflow workflowRun = stepRun.getWorkflow();
    if (workflowRun != null && workflowService.isWorkflowEnded(workflowRun.getId())) {
      log.info(
          "[Chaining] Ignoring external update event for step {} because workflow run {} has ended.",
          stepRun.getId(),
          workflowRun.getId());
      return;
    }

    Optional<Step> stepUpdatedOpt;
    ActionStep actionStep;

    try {
      actionStep = stepService.factoryAction(stepRun.getStepAction(), stepRun.getId());
      stepUpdatedOpt = actionStep.update(stepRun);
    } catch (ChainingException e) {
      // Todo: system notif queue fail + system log for step + status FAIL
      log.error(
          "[Chaining] Update consume : Step (RUN) update failed. Step moved to (END) state. Step ID: {} {}",
          stepRun.getId(),
          e.getMessage(),
          e);
      stepRun.setStatus(StepStatus.END);
      stepService.saveStep(stepRun);
      return;
    }

    if (stepUpdatedOpt.isPresent()) {
      Step stepUpdated = stepUpdatedOpt.get();

      // May move stepUpdated from RUN to END before persisting.
      try {
        actionStep.end(stepUpdated);
      } catch (Exception e) {
        log.error(
            "[Chaining] Update consume: end() completion check failed for Step (RUN). Step ID: {} {}",
            stepUpdated.getId(),
            e.getMessage(),
            e);
        retryEndCheck(stepEvent, stepUpdated);
      }

      stepService.saveStep(stepUpdated);
      try {
        workflowService.evaluateWorkflowProgress(stepUpdated.getWorkflow());
        workflowService.saveWorkflowRun(stepUpdated.getWorkflow());
      } catch (ChainingException e) {
        log.error(
            "[Chaining] Update consume: Evaluation of WORKFLOW Progress has failed with STEP (RUN) update. Workflow ID: {}, Step ID: {}. {}",
            stepUpdated.getWorkflow().getId(),
            stepUpdated.getId(),
            e.getMessage(),
            e);
      }
    }
  }

  /**
   * Retries the end() completion check (bounded, mirrors {@link #handleReadyStepEvent}): the step
   * may already be inject-terminal but end() failed transiently, so re-queue a fresh update event
   * instead of leaving the step stuck in RUN until the workflow times out.
   */
  private void retryEndCheck(ExternalUpdateEvent stepEvent, Step stepUpdated) {
    if (stepEvent.getRetryCount() < chainingConfig.getMaxRetryCount()) {
      stepEvent.setRetryCount(stepEvent.getRetryCount() + 1);
      try {
        queueChainingService.republishUpdateEvent(stepEvent);
      } catch (IOException ioEx) {
        log.error(
            "[Chaining] Failed to re-queue update event for Step ID: {}. Event is lost.",
            stepUpdated.getId(),
            ioEx);
      }
    } else {
      log.error(
          "[Chaining] Step {} end() check failed after {} retries. Step stays RUN until workflow timeout.",
          stepUpdated.getId(),
          chainingConfig.getMaxRetryCount());
    }
  }
}
