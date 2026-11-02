package io.openaev.service.chaining;

import io.openaev.aop.WorkflowUpdateEvent;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Publishes the workflow update events that tell the chaining engine an inject it waits on has
 * changed, and keeps the events the queue refused so that a later publication sends them again.
 *
 * <p>The steps are looked up, and each event's tenant is read, in the tenant that owns the injects
 * named, whatever the scope the caller's transaction was opened with: a step belongs to the tenant
 * of the simulation, atomic testing or autonomous run that owns the inject, and a caller scoped to
 * another tenant would find none and leave the step waiting for the timeout job. That tenant is the
 * changed row's own, read with a projection that no tenant scope narrows, and the work runs in it
 * through {@link WorkflowOwnerScope}, on the caller's own transaction. A kept event carries the
 * tenant it was built with, so it is resent as is, from whatever scope the next publication runs
 * in.
 *
 * <p>The caller must therefore run inside a transaction: the {@link WorkflowUpdateEvent} aspect
 * runs in the annotated method's transaction, and the batched callback handler publishes from each
 * tenant's own scoped transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowUpdateEventPublisher {

  // Keyed by step: a step waiting to be notified needs one event, not one per failed attempt.
  private final Map<String, ExternalUpdateEvent> unsentEvents = new ConcurrentHashMap<>();

  private final QueueChainingService queueChainingService;
  private final StepService stepService;
  private final WorkflowOwnerScope workflowOwnerScope;
  // Read straight from the repositories, as the batched callback handler already does for the same
  // bridge: resolving the owner is the one read here that must stay outside any tenant scope, which
  // is a contract of these projections and not of the services that wrap them.
  private final InjectRepository injectRepository;
  private final InjectExpectationRepository injectExpectationRepository;

  /**
   * Notifies the active step that runs the given inject, if any, then resends the kept events.
   *
   * @param injectId the inject that changed, ignored when blank
   */
  public void publishForInject(String injectId) {
    if (StringUtils.isNotBlank(injectId)) {
      notifyInEachOwnerTenant(
          ownerTenantsOfInjects(Set.of(injectId)),
          () -> {
            Optional<String> activeStep = stepService.findStepIdActiveByInjectId(injectId);
            if (activeStep.isEmpty()) {
              log.info("Step active not found for inject {}", injectId);
            }
            return activeStep.map(Set::of).orElseGet(Set::of);
          });
    }
    resendUnsentEvents();
  }

  /**
   * Notifies the active steps that run the given injects, then resends the kept events.
   *
   * @param injectIds the injects that changed
   */
  public void publishForInjects(Set<String> injectIds) {
    notifyInEachOwnerTenant(
        ownerTenantsOfInjects(injectIds),
        () -> stepService.findStepIdsActiveByInjectIds(injectIds));
    resendUnsentEvents();
  }

  /**
   * Notifies the active steps that run the injects of the given expectations, then resends the kept
   * events.
   *
   * @param expectationIds the expectations that changed
   */
  public void publishForExpectations(Set<String> expectationIds) {
    notifyInEachOwnerTenant(
        ownerTenantsOfExpectations(expectationIds),
        () -> stepService.findStepIdsActiveByExpectationIds(expectationIds));
    resendUnsentEvents();
  }

  /**
   * Looks the active steps up and sends their events once per owning tenant, each time in that
   * tenant's own scope: the lookup, and the step tenant each event is stamped with, are both read
   * there. A batch mixing tenants, which the batched callback handler and the expiration job can
   * produce, is notified once per tenant it touches; an id whose row no longer exists resolves no
   * tenant and is simply not notified.
   */
  private void notifyInEachOwnerTenant(
      Set<String> ownerTenantIds, Supplier<Set<String>> activeSteps) {
    for (String ownerTenantId : ownerTenantIds) {
      workflowOwnerScope.runInOwnerTenant(ownerTenantId, () -> send(activeSteps.get()));
    }
  }

  /**
   * The tenants owning the given injects, read from their own {@code tenant_id} with a native
   * projection that neither the Hibernate tenant filter nor an ambient tenant context changes, so
   * it resolves the owner from a caller that may not be allowed to see it.
   */
  private Set<String> ownerTenantsOfInjects(Set<String> injectIds) {
    if (injectIds == null || injectIds.isEmpty()) {
      return Set.of();
    }
    return injectRepository.findTenantIdsByInjectIds(injectIds).stream()
        .map(row -> (String) row[1])
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * The tenants owning the given expectations, through the inject each belongs to: an expectation
   * carries no tenant of its own, and its inject's tenant is the one its chaining step lives in.
   */
  private Set<String> ownerTenantsOfExpectations(Set<String> expectationIds) {
    if (expectationIds == null || expectationIds.isEmpty()) {
      return Set.of();
    }
    return ownerTenantsOfInjects(
        injectExpectationRepository.findDistinctInjectIdsByInjectExpectationIds(expectationIds));
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
