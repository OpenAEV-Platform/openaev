package io.openaev.service.chaining;

import io.openaev.api.chaining.ActionStep;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
   * Programmatic transaction manager used exclusively for {@link #handleReadyStepEvent}. Do NOT use
   * it elsewhere — prefer {@code @Transactional} on public methods called from outside this class.
   */
  private final TransactionTemplate transactionTemplate;

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
    try {
      // Using TransactionTemplate instead of @Transactional because this method is called from
      // handleReadyEvent() within the same class (self-invocation).
      // We can't put the transactional annotation on handleReadyEvent as it would rollback the
      // whole batch being treated instead of just the one faulty event.
      transactionTemplate.executeWithoutResult(
          status ->
              stepRepository
                  .findById(stepEvent.getStepId())
                  .ifPresentOrElse(
                      this::run,
                      () ->
                          log.error(
                              "Ready consume: Step not found for StepEvent ID: {}",
                              stepEvent.getStepId())));
    } catch (Exception e) {
      if (stepEvent.getRetryCount() < chainingConfig.getMaxRetryCount()) {
        stepEvent.setRetryCount(stepEvent.getRetryCount() + 1);
        log.warn(
            "Transaction failed for StepEvent {}. Re-queuing (retry {}/{}).",
            stepEvent.getStepId(),
            stepEvent.getRetryCount(),
            chainingConfig.getMaxRetryCount(),
            e);
        try {
          queueChainingService.republishReadyEvent(stepEvent);
        } catch (IOException ioEx) {
          log.error(
              "Failed to re-queue StepEvent {} after transactional failure. Event is lost.",
              stepEvent.getStepId(),
              ioEx);
        }
      } else {
        log.error(
            "Transaction failed for StepEvent {} after {} retries. Event is dropped.",
            stepEvent.getStepId(),
            chainingConfig.getMaxRetryCount(),
            e);
      }
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
          "Ignoring run request for step {} because workflow run {} has ended.",
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
          "Ready consume : Step (READY) execution failed. Step moved to (END) state. Step ID: {} {}",
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
  @Transactional(rollbackFor = Exception.class)
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
    Step stepRun;
    try {
      stepRun = stepService.findByIdAndStatus(stepEvent.getStepId(), StepStatus.RUN);
    } catch (ElementNotFoundException e) {
      // Todo: system notif queue fail + system log for step + status FAIL
      log.error(
          "Update consume: Step (RUN) not found. Step ID: {} {}",
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
          "Ignoring external update event for step {} because workflow run {} has ended.",
          stepRun.getId(),
          workflowRun.getId());
      return;
    }

    Optional<Step> stepUpdatedOpt;

    try {
      ActionStep actionStep = stepService.factoryAction(stepRun.getStepAction(), stepRun.getId());
      stepUpdatedOpt = actionStep.update(stepRun);
    } catch (ChainingException e) {
      // Todo: system notif queue fail + system log for step + status FAIL
      log.error(
          "Update consume : Step (RUN) update failed. Step moved to (END) state. Step ID: {} {}",
          stepRun.getId(),
          e.getMessage(),
          e);
      stepRun.setStatus(StepStatus.END);
      stepService.saveStep(stepRun);
      return;
    }

    if (stepUpdatedOpt.isPresent()) {
      Step stepUpdated = stepUpdatedOpt.get();
      stepService.saveStep(stepUpdated);
      try {
        workflowService.evaluateWorkflowProgress(stepUpdated.getWorkflow());
        workflowService.saveWorkflowRun(stepUpdated.getWorkflow());
      } catch (ChainingException e) {
        log.error(
            "Update consume: Evaluation of WORKFLOW Progress has failed with STEP (RUN) update. Workflow ID: {}, Step ID: {}. {}",
            stepUpdated.getWorkflow().getId(),
            stepUpdated.getId(),
            e.getMessage(),
            e);
      }
    }
  }
}
