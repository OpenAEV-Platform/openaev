package io.openaev.service.chaining;

import io.openaev.aop.WorkflowUpdateEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Publishes the workflow update events that tell the chaining engine an inject it waits on has
 * changed, and keeps the events the queue refused so that a later publication sends them again.
 *
 * <p>The steps are looked up, and each event's tenant is read, in the caller's transaction, so the
 * caller must already carry the scope of the injects it names: the {@link WorkflowUpdateEvent}
 * aspect runs in the annotated method's transaction, and the batched callback handler publishes
 * from each tenant's own scoped transaction. A kept event carries the tenant it was built with, so
 * it is resent as is, from whatever scope the next publication runs in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowUpdateEventPublisher {

  // Keyed by step: a step waiting to be notified needs one event, not one per failed attempt.
  private final Map<String, ExternalUpdateEvent> unsentEvents = new ConcurrentHashMap<>();

  private final QueueChainingService queueChainingService;
  private final StepService stepService;

  /**
   * Notifies the active step that runs the given inject, if any, then resends the kept events.
   *
   * @param injectId the inject that changed, ignored when blank
   */
  public void publishForInject(String injectId) {
    if (StringUtils.isNotBlank(injectId)) {
      stepService
          .findStepIdActiveByInjectId(injectId)
          .ifPresentOrElse(
              stepId -> send(Set.of(stepId)),
              () -> log.info("Step active not found for inject {}", injectId));
    }
    resendUnsentEvents();
  }

  /**
   * Notifies the active steps that run the given injects, then resends the kept events.
   *
   * @param injectIds the injects that changed
   */
  public void publishForInjects(Set<String> injectIds) {
    send(stepService.findStepIdsActiveByInjectIds(injectIds));
    resendUnsentEvents();
  }

  /**
   * Notifies the active steps that run the injects of the given expectations, then resends the kept
   * events.
   *
   * @param expectationIds the expectations that changed
   */
  public void publishForExpectations(Set<String> expectationIds) {
    send(stepService.findStepIdsActiveByExpectationIds(expectationIds));
    resendUnsentEvents();
  }

  private void send(Set<String> stepIds) {
    Iterator<String> remaining = stepIds.iterator();
    while (remaining.hasNext()) {
      String stepId = remaining.next();
      // A send supersedes only the event kept before it: one another thread keeps meanwhile stays.
      ExternalUpdateEvent keptBeforeSend = unsentEvents.get(stepId);
      try {
        queueChainingService.updateStep(stepId);
        if (keptBeforeSend != null) {
          unsentEvents.remove(stepId, keptBeforeSend);
        }
      } catch (IOException e) {
        // The queue is most likely down: keep this event and the rest without trying them.
        keep(stepId);
        remaining.forEachRemaining(this::keep);
        return;
      }
    }
  }

  private void keep(String stepId) {
    log.warn("[Chaining] Update event for step {} not sent, kept for a later retry", stepId);
    unsentEvents.put(stepId, queueChainingService.newUpdateEvent(stepId));
  }

  private void resendUnsentEvents() {
    for (ExternalUpdateEvent event : List.copyOf(unsentEvents.values())) {
      try {
        event.setEmissionDate(Instant.now().toEpochMilli());
        queueChainingService.republishUpdateEvent(event);
        unsentEvents.remove(event.getStepId(), event);
      } catch (IOException e) {
        return;
      }
    }
  }
}
