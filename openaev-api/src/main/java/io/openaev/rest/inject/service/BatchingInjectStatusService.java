package io.openaev.rest.inject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.Agent;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.queue.BatchQueueService;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
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

  /**
   * Handle the list of inject execution callbacks
   *
   * @param injectExecutionCallbacks the inject execution callbacks
   */
  @LogExecutionTime
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public List<InjectExecutionCallback> handleInjectExecutionCallback(
      List<InjectExecutionCallback> injectExecutionCallbacks) {

    List<InjectExecutionCallback> successfullyProcessedCallbacks = new ArrayList<>();

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
    return successfullyProcessedCallbacks;
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
        log.warn("Unable to requeue inject execution callback, keeping it in memory for retry", e);
        break;
      }
    }
  }
}
