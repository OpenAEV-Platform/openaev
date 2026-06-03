package io.openaev.service.chaining;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.openaev.api.chaining.ActionStep;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles step lifecycle events consumed from the chaining queues: ready events (execute a step)
 * and external update events (propagate external results back into the workflow).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StepEventService implements StepEventHandler, ExternalUpdateEventHandler {

  /**
   * JSON field name used to carry the rate-limit reschedule count through the delay queue. Stored
   * inside the step's {@code input} JSON so it survives step re-creation and can be read by {@link
   * io.openaev.api.chaining.InjectExecutionStep} to emit an INFO trace once the inject is finally
   * executed.
   */
  public static final String RATE_LIMIT_COUNT_FIELD = "_rateLimitCount";

  private final StepService stepService;
  private final WorkflowService workflowService;
  private final StepRepository stepRepository;
  private final RateLimitGuardService rateLimitGuardService;
  private final StepDelayQueueService stepDelayQueueService;

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
  @Transactional(rollbackFor = Exception.class)
  public void handleReadyStepEvent(StepEvent stepEvent) {
    stepRepository
        .findById(stepEvent.getStepId())
        .ifPresentOrElse(
            this::run,
            () ->
                log.error(
                    "Ready consume: Step not found for StepEvent ID: {}", stepEvent.getStepId()));
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

    // Guard: rate limit — re-schedule the step if the workflow has reached its rate limit.
    if (workflowRun != null && !rateLimitGuardService.isExecutionAllowed(workflowRun)) {
      long backoffSeconds =
          workflowRun.getMaxTemporalRateSeconds() != null
              ? workflowRun.getMaxTemporalRateSeconds()
              : 60L;
      // Track rate limit count in step input so the info propagates through the delay queue
      // and can be surfaced as an INFO trace when the inject is eventually executed.
      String input = stepReady.getInput() != null ? stepReady.getInput() : "{}";
      int currentCount = getRateLimitCount(input);
      Gson gson = new Gson();
      JsonObject json = gson.fromJson(input, JsonObject.class);
      json.addProperty(RATE_LIMIT_COUNT_FIELD, currentCount + 1);
      stepReady.setInput(json.toString());
      stepDelayQueueService.reschedule(stepReady, backoffSeconds);
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

  /**
   * Extracts the rate-limit reschedule count from the step input JSON.
   *
   * @param input the step input JSON string
   * @return the current rate-limit count, or 0 if not present
   */
  public static int getRateLimitCount(String input) {
    if (input == null) {
      return 0;
    }
    try {
      String value = StepService.getField(input, RATE_LIMIT_COUNT_FIELD);
      return value != null ? Integer.parseInt(value) : 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
