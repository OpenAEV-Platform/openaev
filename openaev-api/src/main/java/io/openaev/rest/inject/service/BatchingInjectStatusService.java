package io.openaev.rest.inject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional
public class BatchingInjectStatusService {

  private final InjectRepository injectRepository;
  private final AgentRepository agentRepository;
  private final StructuredOutputUtils structuredOutputUtils;
  private final InjectExecutionService injectExecutionService;

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

    // Getting all the injects needed all at once
    Map<String, Inject> mapInjectsById =
        injectRepository
            .findAllByIdWithExpectations(
                injectExecutionCallbacks.stream()
                    .map(InjectExecutionCallback::getInjectId)
                    .toList())
            .stream()
            .collect(Collectors.toMap(Inject::getId, Function.identity()));

    // Getting all the agents all at once
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
            // in a
            // coherent state.
            // This prevents issues where the PENDING status took more time to persist than it took
            // for
            // the agent to send the complete action.
            // FIXME: At the moment, this whole function is only called by our implant. These
            // implant are
            // launched with the async value to true, which force the implant to go from EXECUTING
            // to
            // PENDING, before going to EXECUTED.
            // So if in the future, this function is called to update a synchronous inject, we will
            // need
            // to find a way to get the async boolean somehow and add it to this condition.
            if (callback
                    .getInjectExecutionInput()
                    .getAction()
                    .equals(InjectExecutionAction.complete)
                && (inject.getStatus().isEmpty()
                    || !inject.getStatus().get().getName().equals(ExecutionStatus.PENDING))) {
              // If we receive a status update with a terminal state status, we must first check
              // that the
              // current status is in the PENDING state
              log.warn(
                  String.format(
                      "Received a complete action for inject %s with status %s, but current status is not PENDING",
                      callback.getInjectId(),
                      inject.getStatus().map(is -> is.getName().toString()).orElse("unknown")));
              throw new DataIntegrityViolationException(
                  "Cannot complete inject that is not in PENDING state");
            }
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

            // Extract the output parsers
            Set<OutputParser> outputParsers = structuredOutputUtils.extractOutputParsers(inject);

            // Process the execution trace
            injectExecutionService.processInjectExecution(
                inject, agent, callback.getInjectExecutionInput(), outputParsers);
            successfullyProcessedCallbacks.add(callback);
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
}
