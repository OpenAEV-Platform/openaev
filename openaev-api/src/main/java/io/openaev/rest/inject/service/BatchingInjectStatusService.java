package io.openaev.rest.inject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.service.InjectExpectationService;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  @LogExecutionTime
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void handleInjectExecutionCallback(
      List<InjectExecutionCallback> injectExecutionCallbacks) {

    Instant start = Instant.now();

    Map<String, Inject> mapInjectsById =
        injectRepository
            .findAllByIdWithExpectations(
                injectExecutionCallbacks.stream()
                    .map(InjectExecutionCallback::getInjectId)
                    .toList())
            .stream()
            .collect(Collectors.toMap(Inject::getId, Function.identity()));

    Instant postGetInject = Instant.now();

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

    Instant postGetAgent = Instant.now();

    Map<String, List<Long>> mapTimeSpent =
        Map.of(
            "ExtractOutput",
            new ArrayList<>(),
            "ComputeOutput",
            new ArrayList<>(),
            "Process",
            new ArrayList<>(),
            "HandleError",
            new ArrayList<>());

    Map<String, List<Long>> mapTimeSpentProcess =
        Map.of(
            "structured",
            new ArrayList<>(),
            "computeStructured",
            new ArrayList<>(),
            "checkCveExpectation",
            new ArrayList<>(),
            "updateInjectStatus",
            new ArrayList<>(),
            "addEndDate",
            new ArrayList<>(),
            "extractFindings",
            new ArrayList<>());

    injectExecutionCallbacks.forEach(
        callback -> {
          Inject inject = null;

          try {
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
            Agent agent =
                Optional.ofNullable(mapAgentsById.get(callback.getAgentId()))
                    .orElseThrow(
                        () ->
                            new ElementNotFoundException(
                                "Agent not found: " + callback.getAgentId()));

            Instant beforeExtract = Instant.now();
            Set<OutputParser> outputParsers = structuredOutputUtils.extractOutputParsers(inject);
            Instant afterExtract = Instant.now();

            Map<String, Long> results =
                injectExecutionService.processInjectExecution(
                    inject, agent, callback.getInjectExecutionInput(), outputParsers);
            mapTimeSpentProcess.forEach(
                (s, longs) -> {
                  longs.add(results.get(s));
                });
            Instant afterProcess = Instant.now();
            mapTimeSpent
                .get("ExtractOutput")
                .add(afterExtract.toEpochMilli() - beforeExtract.toEpochMilli());
            mapTimeSpent
                .get("Process")
                .add(afterProcess.toEpochMilli() - afterExtract.toEpochMilli());
          } catch (ElementNotFoundException e) {
            Instant beforeError = Instant.now();
            injectExecutionService.handleInjectExecutionError(inject, e);
            Instant afterError = Instant.now();
            mapTimeSpent
                .get("HandleError")
                .add(afterError.toEpochMilli() - beforeError.toEpochMilli());
          }
        });

    Instant totalTime = Instant.now();
    log.warn(
        String.format(
            "Time spent breakdown on one cycle of insertion %d processed :%n total : %d ms%n - time to get injects : %d ms %n - time to get agents : %d ms %n - time to process all the injectCallbacks : %d ms %n - mean time to extract output : %f ms %n - mean time to compute output : %f ms %n - mean time to process : %f ms %n - mean time to deal with errors : %f ms %n",
            injectExecutionCallbacks.size(),
            totalTime.toEpochMilli() - start.toEpochMilli(),
            postGetInject.toEpochMilli() - start.toEpochMilli(),
            postGetAgent.toEpochMilli() - postGetInject.toEpochMilli(),
            totalTime.toEpochMilli() - postGetAgent.toEpochMilli(),
            mapTimeSpent.get("ExtractOutput").stream()
                .mapToLong(value -> value)
                .average()
                .orElse(0),
            mapTimeSpent.get("ComputeOutput").stream()
                .mapToLong(value -> value)
                .average()
                .orElse(0),
            mapTimeSpent.get("Process").stream().mapToLong(value -> value).average().orElse(0),
            mapTimeSpent.get("HandleError").stream()
                .mapToLong(value -> value)
                .average()
                .orElse(0)));

    log.warn(
        String.format(
            "Time spent on process breakdown on one cycle of insertion %d processed :%n - Time on structured : %f ms %n - Time on computeStructured : %f ms %n - Time on checkCveExpectation : %f ms %n - Time on updateInjectStatus : %f ms %n - Time on addEndDate : %f ms %n - Time on extractFindings : %f ms %n",
            injectExecutionCallbacks.size(),
            mapTimeSpentProcess.get("structured").stream()
                .mapToLong(value -> value)
                .average()
                .orElse(0),
            mapTimeSpentProcess.get("computeStructured").stream()
                .mapToLong(value -> value)
                .average()
                .orElse(0),
            mapTimeSpentProcess.get("checkCveExpectation").stream()
                .mapToLong(value -> value)
                .average()
                .orElse(0),
            mapTimeSpentProcess.get("updateInjectStatus").stream()
                .mapToLong(value -> value)
                .average()
                .orElse(0),
            mapTimeSpentProcess.get("addEndDate").stream()
                .mapToLong(value -> value)
                .average()
                .orElse(0),
            mapTimeSpentProcess.get("extractFindings").stream()
                .mapToLong(value -> value)
                .average()
                .orElse(0)));
  }
}
