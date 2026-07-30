package io.openaev.service.inject;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.WorkflowUpdateEvent;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.rest.inject.service.StructuredOutputUtils;
import io.openaev.service.queue.BatchQueueService;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional
public class BatchingInjectStatusService {

  private static final int MAX_RETRIES = 5;
  // Inmemory queue system to add delay to actual re-queuing mechanism
  private final Queue<InjectExecutionCallback> callbacksToRequeue = new ConcurrentLinkedQueue<>();

  private final InjectRepository injectRepository;
  private final AgentRepository agentRepository;
  private final StructuredOutputUtils structuredOutputUtils;
  private final InjectExecutionService injectExecutionService;

  // Set from InjectApi.init() function. I preferred that to creating a dedicated @Bean instance
  // to avoid making too big changes.
  // Also, it can be null when the inject-trace queue is not configured (e.g. legacy mode, tests).
  @Setter private BatchQueueService<InjectExecutionCallback> injectTraceQueueService;

  @Resource protected ObjectMapper mapper;

  private final TenantScopedTransaction tenantTx;

  /**
   * Handles the batched inject-execution callbacks consumed from the inject-trace queue. The batch
   * mixes tenants, so callbacks are grouped by their inject's tenant (resolved via a native,
   * filter-exempt projection) and each group is processed under its own tenant scope (v1 {@code
   * TenantContext} + v2 {@code TxCtx}, set-then-finally-clear on the pooled worker thread), the
   * same hardening applied to the chaining consumer (#6357 / #6904). A null or unknown tenant falls
   * back to the default tenant, so mono-tenant deployments keep their previous behaviour.
   *
   * <p>Deliberately NOT run in the class-level transaction: a batch-level transaction would open
   * before the per-tenant scope and leave the batch running outside any tenant, so {@code
   * NOT_SUPPORTED} suspends it and each group opens its own scoped transaction.
   *
   * @param injectExecutionCallbacks the inject execution callbacks
   */
  @LogExecutionTime
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @WorkflowUpdateEvent(injectIds = "#injectExecutionCallbacks.![injectId]")
  public List<InjectExecutionCallback> handleInjectExecutionCallback(
      List<InjectExecutionCallback> injectExecutionCallbacks) {
    List<InjectExecutionCallback> successfullyProcessedCallbacks = new ArrayList<>();
    groupCallbacksByTenant(injectExecutionCallbacks)
        .forEach(
            (tenantId, tenantCallbacks) -> {
              TenantContext.setCurrentTenant(tenantId);
              try {
                tenantTx.execute(
                    TxCtx.forTenant(tenantId),
                    () -> processTenantCallbacks(tenantCallbacks, successfullyProcessedCallbacks));
              } finally {
                TenantContext.clearCurrentTenant();
              }
            });
    return successfullyProcessedCallbacks;
  }

  /**
   * Groups callbacks by the tenant that owns their inject, resolved via a native filter-exempt
   * projection (the batch worker carries no ambient tenant). Preserves arrival order within a
   * group; an inject whose tenant cannot be resolved falls back to the default tenant.
   */
  private Map<String, List<InjectExecutionCallback>> groupCallbacksByTenant(
      List<InjectExecutionCallback> injectExecutionCallbacks) {
    Set<String> injectIds =
        injectExecutionCallbacks.stream()
            .map(InjectExecutionCallback::getInjectId)
            .collect(Collectors.toSet());
    Map<String, String> tenantByInjectId = new HashMap<>();
    if (!injectIds.isEmpty()) {
      for (Object[] row : injectRepository.findTenantIdsByInjectIds(injectIds)) {
        tenantByInjectId.put((String) row[0], (String) row[1]);
      }
    }
    Map<String, List<InjectExecutionCallback>> callbacksByTenant = new LinkedHashMap<>();
    for (InjectExecutionCallback callback : injectExecutionCallbacks) {
      String tenantId = tenantByInjectId.getOrDefault(callback.getInjectId(), DEFAULT_TENANT_UUID);
      callbacksByTenant.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(callback);
    }
    return callbacksByTenant;
  }

  /**
   * Processes one tenant's callbacks under the already-active tenant scope: bulk-loads that
   * tenant's injects and agents, then handles each callback in chronological order. The
   * per-callback logic is unchanged; only the previous cross-tenant filter disabling was dropped in
   * favour of the scope.
   */
  private void processTenantCallbacks(
      List<InjectExecutionCallback> injectExecutionCallbacks,
      List<InjectExecutionCallback> successfullyProcessedCallbacks) {

    // Getting all the injects linked to the list of execution traces, all at once
    Map<String, Inject> mapInjectsById =
        injectRepository
            .findAllByIdWithExpectations(
                injectExecutionCallbacks.stream()
                    .map(InjectExecutionCallback::getInjectId)
                    .toList())
            .stream()
            .collect(Collectors.toMap(Inject::getId, Function.identity()));

    // Getting all the agents linked to the list of execution traces, all at once
    Map<String, Agent> mapAgentsById =
        StreamSupport.stream(
                agentRepository
                    .findAllById(
                        injectExecutionCallbacks.stream()
                            .map(InjectExecutionCallback::getAgentId)
                            .toList())
                    .spliterator(),
                false)
            .collect(Collectors.toMap(Agent::getId, Function.identity()));

    // Sorting the inject execution callbacks to make sure we handle them in chronological order
    Stream<InjectExecutionCallback> sortedInjectExecutionCallbacks =
        injectExecutionCallbacks.stream()
            .sorted(Comparator.comparing(InjectExecutionCallback::getEmissionDate));

    // For each of the callback
    sortedInjectExecutionCallbacks.forEach(
        callback -> {
          Inject inject = null;

          try {
            // Get the inject or throw if not found
            inject =
                Optional.ofNullable(mapInjectsById.get(callback.getInjectId()))
                    .orElseThrow(
                        () ->
                            new ElementNotFoundException(
                                "Inject not found: " + callback.getInjectId()));
            // issue/3550: added this condition to ensure we only update statuses if the inject is
            // in a coherent state.
            // This prevents issues where the PENDING status took more time to persist than it took
            // for the agent to send the complete action.
            // FIXME: At the moment, this whole function is only called by execution traces created
            // form our implants.
            // These implants are launched with the async value to true, which force the implant to
            // go from EXECUTING to PENDING, before going to EXECUTED.
            // So if in the future, this function is called to update a synchronous inject, we will
            // need to find a way to get the async boolean somehow and add it to this condition.
            if (callback
                    .getInjectExecutionInput()
                    .getAction()
                    .equals(InjectExecutionAction.complete)
                && (inject.getStatus().isEmpty()
                    || !inject.getStatus().get().getName().equals(ExecutionStatus.PENDING))) {
              // If we receive a status update with a terminal state status, we must first check
              // that the current status is in the PENDING state
              log.warn(
                  String.format(
                      "Received a complete action for inject %s with status %s, but current status is not PENDING (retry %d/%d)",
                      callback.getInjectId(),
                      inject.getStatus().map(is -> is.getName().toString()).orElse("unknown"),
                      callback.getRetryCount(),
                      MAX_RETRIES));
              if (callback.getRetryCount() < MAX_RETRIES && injectTraceQueueService != null) {
                callback.setRetryCount(callback.getRetryCount() + 1);
                // We change the emission date to current timestamp here to be more accurate
                // order has become meaningless in case of re-queueing the message any way
                callback.setEmissionDate(Instant.now().toEpochMilli());
                callbacksToRequeue.add(callback);
              } else {
                if (callback.getRetryCount() < MAX_RETRIES) {
                  log.warn(
                      "Inject trace queue service is not configured, saving trace directly for inject {}",
                      callback.getInjectId());
                } else {
                  log.warn("Max retries reached for inject {}", callback.getInjectId());
                }
                // Max retry reached, we save the trace anyway, to make sure no information is lost
                // and let the expiration manager logic handle the discrepancies if any exists
                saveExecutionTrace(callback, mapAgentsById, inject, successfullyProcessedCallbacks);
              }
            } else {
              saveExecutionTrace(callback, mapAgentsById, inject, successfullyProcessedCallbacks);
            }
          } catch (ElementNotFoundException e) {
            injectExecutionService.handleInjectExecutionError(inject, e);
            successfullyProcessedCallbacks.add(callback);
          } catch (Exception e) {
            log.warn(
                "The was a problem processing the element for the inject {} and agent {}",
                callback.getInjectId(),
                callback.getAgentId(),
                e);
          }
        });
  }

  /**
   * Save the execution trace and compute the inject status
   *
   * @param callback the execution trace message to handle
   * @param mapAgentsById map of agents linked to the execution traces
   * @param inject the inject linked to the given execution trace
   * @param successfullyProcessedCallbacks list of successfully processed execution trace messages
   */
  private void saveExecutionTrace(
      InjectExecutionCallback callback,
      Map<String, Agent> mapAgentsById,
      Inject inject,
      List<InjectExecutionCallback> successfullyProcessedCallbacks) {
    // Get the nullable agent; throw only if ID was supplied and not found
    Agent agent =
        Optional.ofNullable(callback.getAgentId())
            .map(
                id ->
                    Optional.ofNullable(mapAgentsById.get(callback.getAgentId()))
                        .orElseThrow(
                            () ->
                                new ElementNotFoundException(
                                    "Agent not found: " + callback.getAgentId())))
            .orElse(null);

    // Process the execution trace
    if (agent == null) {
      injectExecutionService.processInjectExecutionWithInjector(
          inject, callback.getInjectExecutionInput());
    } else {
      injectExecutionService.processInjectExecutionWithAgent(
          inject, agent, callback.getInjectExecutionInput());
    }
    successfullyProcessedCallbacks.add(callback);
  }

  /**
   * Requeue all callbacks that were received too soon compared to the inject status This is called
   * from a quartz job
   */
  public void requeueCallbacks() throws IOException {
    if (injectTraceQueueService == null) {
      return;
    }
    InjectExecutionCallback callback;
    while ((callback = callbacksToRequeue.peek()) != null) {
      try {
        injectTraceQueueService.publish(callback);
        callbacksToRequeue.poll();
      } catch (Exception e) {
        log.warn(
            "Unable to requeue inject execution callback injectId="
                + callback.getInjectId()
                + " agentId="
                + callback.getAgentId()
                + " retryCount="
                + callback.getRetryCount()
                + " message="
                + (callback.getInjectExecutionInput() != null
                    ? callback.getInjectExecutionInput().getMessage()
                    : null)
                + " outputStructured="
                + (callback.getInjectExecutionInput() != null
                    ? callback.getInjectExecutionInput().getOutputStructured()
                    : null)
                + " outputRaw="
                + (callback.getInjectExecutionInput() != null
                    ? callback.getInjectExecutionInput().getOutputRaw()
                    : null)
                + " , keeping it in memory for retry",
            e);
        break;
      }
    }
  }
}
