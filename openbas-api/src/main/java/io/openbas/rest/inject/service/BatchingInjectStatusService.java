package io.openbas.rest.inject.service;

import static io.openbas.utils.InjectExecutionUtils.convertExecutionAction;
import static io.openbas.utils.InjectExecutionUtils.convertExecutionStatus;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openbas.database.helper.InjectStatusRepositoryHelper;
import io.openbas.database.model.*;
import io.openbas.database.repository.*;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.finding.FindingService;
import io.openbas.rest.inject.form.InjectExecutionCallback;
import io.openbas.rest.inject.form.InjectExecutionInput;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class BatchingInjectStatusService {

  private final AgentRepository agentRepository;
  private final InjectStatusRepository injectStatusRepository;
  private final FindingService findingService;
  private final InjectStatusRepositoryHelper injectStatusRepositoryHelper;
  private final StructuredOutputUtils structuredOutputUtils;
  private final OutputParserRepository outputParserRepository;

  /**
   * Update the final status of the inject
   *
   * @param simpleInjectStatus the inject status corresponding
   * @param statuses the statuses of the execution traces
   */
  public void updateFinalInjectStatus(
      SimpleInjectStatus simpleInjectStatus, List<ExecutionTraceStatus> statuses) {
    // Deducing the final status
    ExecutionStatus finalStatus = computeStatus(statuses);

    // Updating the inject status accordingly
    simpleInjectStatus.setName(finalStatus);
  }

  /**
   * Create an execution trace from the inject status, the input and the agent
   *
   * @param injectStatusId the inject status Id
   * @param input the input
   * @param agentId the agent Id
   * @return the newly created ExecutionTrace
   */
  public SimpleExecutionTrace createExecutionTrace(
      String injectStatusId, InjectExecutionInput input, String agentId) {
    ExecutionTraceAction executionAction = convertExecutionAction(input.getAction());
    // Convert the string of the status into an ExecutionTraceStatus
    ExecutionTraceStatus traceStatus = ExecutionTraceStatus.valueOf(input.getStatus());
    // Create the ExecutionTrace per se
    return new SimpleExecutionTrace(
        injectStatusId, traceStatus, null, input.getMessage(), executionAction, agentId, null);
  }

  /**
   * Update the status of the execution trace
   *
   * @param traces the traces already on the inject associated
   * @param executionTrace the execution trace to update
   * @param agentId the agent id
   */
  private void computeExecutionTraceStatusIfNeeded(
      List<SimpleExecutionTrace> traces, SimpleExecutionTrace executionTrace, String agentId) {
    // Figuring out the execution trace status
    ExecutionTraceStatus traceStatus =
        convertExecutionStatus(
            computeStatus(
                traces.stream()
                    .filter(t -> t.getAgentId() != null)
                    .filter(t -> t.getAgentId().equals(agentId))
                    .map(SimpleExecutionTrace::getStatus)
                    .toList()));
    // Updating the status in the execution trace
    executionTrace.setStatus(traceStatus);
  }

  /**
   * Update the inject status, creates the execution trace and adding it to the inject status
   *
   * @param agentId the agent ID
   * @param injectStatus the inject status to update
   * @param input the inputs
   * @return the newly created execution trace
   */
  public SimpleExecutionTrace updateInjectStatus(
      String agentId,
      SimpleInjectStatus injectStatus,
      List<SimpleExecutionTrace> traces,
      InjectExecutionInput input) {
    SimpleExecutionTrace executionTrace =
        createExecutionTrace(injectStatus.getId(), input, agentId);
    // If we do have an agent and that our execution trace means we finished execution
    if (agentId != null && executionTrace.getAction().equals(ExecutionTraceAction.COMPLETE)) {
      computeExecutionTraceStatusIfNeeded(traces, executionTrace, agentId);
    }
    return executionTrace;
  }

  public boolean isAllInjectAgentsExecuted(List<SimpleExecutionTrace> traces, int numberAgent) {
    int totalCompleteTrace =
        traces.stream()
            .filter(
                simpleExecutionTrace ->
                    simpleExecutionTrace.getAction() == ExecutionTraceAction.COMPLETE)
            .map(SimpleExecutionTrace::getAgentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList()
            .size();
    return numberAgent == totalCompleteTrace;
  }

  @Transactional
  public void handleInjectExecutionCallbackList(
      List<InjectExecutionCallback> injectExecutionCallbacks) {

    // Sorting the objects we need to take care of by date
    List<InjectExecutionCallback> sortedInjectExecutionCallbacks =
        injectExecutionCallbacks.stream()
            .sorted(Comparator.comparing(InjectExecutionCallback::getEmissionDate))
            .toList();

    Map<String, SimpleInjectStatus> simpleInjectStatus =
        injectStatusRepositoryHelper.getSimpleInjectStatusesByInjectId(
            sortedInjectExecutionCallbacks.stream()
                .map(InjectExecutionCallback::getInjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

    Map<String, List<SimpleExecutionTrace>> mapSimpleExecutionTraceByInjectStatusId =
        injectStatusRepositoryHelper.getSimpleExecutionTracesByInjectStatusId(
            simpleInjectStatus.values().stream().map(SimpleInjectStatus::getId).toList());

    Map<String, Integer> mapAssetNumberByInjectId =
        injectStatusRepositoryHelper.getAssetsNumberByInjectId(
            sortedInjectExecutionCallbacks.stream()
                .map(InjectExecutionCallback::getInjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

    Map<String, String> mapAssetIdByAgentId =
        agentRepository
            .assetIdByAgentId(
                injectExecutionCallbacks.stream().map(InjectExecutionCallback::getAgentId).toList())
            .stream()
            .collect(
                Collectors.toMap(
                    objects -> objects[0].toString(), objects -> objects[1].toString()));

    Map<String, List<OutputParserByInject>> outputParserByInjectId =
        outputParserRepository
            .findAllOutputParsersByInjectIds(
                sortedInjectExecutionCallbacks.stream()
                    .map(InjectExecutionCallback::getInjectId)
                    .filter(Objects::nonNull)
                    .toList())
            .stream()
            .collect(Collectors.groupingBy(OutputParserByInject::getInjectId));

    // Preparing a list of injects we need to save
    Set<SimpleInjectStatus> injectsStatusToSave = new HashSet<>();
    Set<SimpleExecutionTrace> executionTraceToSave = new HashSet<>();
    Set<SimpleFinding> findingToSave = new HashSet<>();

    // For each of the trace
    for (InjectExecutionCallback injectExecutionCallback : sortedInjectExecutionCallbacks) {
      try {
        if (injectExecutionCallback.getAgentId() != null
            && mapAssetIdByAgentId.get(injectExecutionCallback.getAgentId()) == null) {
          throw new ElementNotFoundException(
              "Agent not found: " + injectExecutionCallback.getAgentId());
        }
        // Getting the inject or throwing an exception
        SimpleInjectStatus injectStatus =
            simpleInjectStatus.get(injectExecutionCallback.getInjectId());
        if (injectStatus == null) {
          log.error("Inject not found: {}", injectExecutionCallback.getInjectId());
          throw new ElementNotFoundException(
              String.format("Inject not found: %s", injectExecutionCallback.getInjectId()));
        }

        // -- UPDATE STATUS --
        List<SimpleExecutionTrace> traces =
            mapSimpleExecutionTraceByInjectStatusId.getOrDefault(
                injectStatus.getId(), Collections.emptyList());
        SimpleExecutionTrace trace =
            updateInjectStatus(
                injectExecutionCallback.getAgentId(),
                injectStatus,
                traces,
                injectExecutionCallback.getInjectExecutionInput());
        mapSimpleExecutionTraceByInjectStatusId.computeIfAbsent(
            trace.getInjectStatusId(), k -> new ArrayList<>());
        mapSimpleExecutionTraceByInjectStatusId.get(trace.getInjectStatusId()).add(trace);

        // If the execution trace is complete, we update the final status of the inject
        if (trace.getAction().equals(ExecutionTraceAction.COMPLETE)
            && (injectExecutionCallback.getAgentId() == null
                || isAllInjectAgentsExecuted(
                    mapSimpleExecutionTraceByInjectStatusId.get(trace.getInjectStatusId()),
                    mapAssetNumberByInjectId.get(injectExecutionCallback.getInjectId())))) {
          updateFinalInjectStatus(
              injectStatus, traces.stream().map(SimpleExecutionTrace::getStatus).toList());
        }

        executionTraceToSave.add(trace);
        injectsStatusToSave.add(injectStatus);

        Set<OutputParser> outputParsers =
            new HashSet<>(
                outputParserByInjectId.get(injectExecutionCallback.getInjectId()).stream()
                    .map(OutputParserByInject::getOutputParser)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
        Optional<ObjectNode> structuredOutput =
            structuredOutputUtils.computeStructuredOutput(
                outputParsers, injectExecutionCallback.getInjectExecutionInput());

        ObjectNode structured = structuredOutput.orElse(null);

        if (structured != null) {
          if (injectExecutionCallback.getAgentId() != null) {
            // Extract findings from structured outputs generated by the output parsers specified in
            // the
            // payload, typically derived from the raw output of the implant execution.
            findingToSave.addAll(
                findingService.extractFindingsFromOutputParsers(
                    injectExecutionCallback.getInjectId(),
                    mapAssetIdByAgentId.get(injectExecutionCallback.getAgentId()),
                    outputParsers,
                    structured));
          } else {
            // Structured output directly provided (e.g., from injectors)
            findingToSave.addAll(
                findingService.extractFindingsFromInjectorContract(
                    injectExecutionCallback.getInjectId(),
                    outputParserByInjectId
                        .get(injectExecutionCallback.getInjectId())
                        .getFirst()
                        .getInjectorContract(),
                    structured));
          }
        }

      } catch (ElementNotFoundException e) {
        // If we have the inject, we add an error message in it
        log.error(e.getMessage(), e);
        SimpleInjectStatus injectStatus =
            simpleInjectStatus.get(injectExecutionCallback.getInjectId());
        if (injectStatus != null) {
          SimpleExecutionTrace trace =
              new SimpleExecutionTrace(
                  injectStatus.getId(),
                  ExecutionTraceStatus.ERROR,
                  null,
                  e.getMessage(),
                  ExecutionTraceAction.COMPLETE,
                  null,
                  Instant.now());
          executionTraceToSave.add(trace);
          injectsStatusToSave.add(injectStatus);
        }
      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }
    }

    Set<SimpleFinding> agregatedFindingsToSave = new HashSet<>();
    for (SimpleFinding simpleFinding : findingToSave) {
      Optional<SimpleFinding> alreadyExistingFinding =
          agregatedFindingsToSave.stream()
              .filter(
                  finding ->
                      simpleFinding.getInjectId().equals(finding.getInjectId())
                          && simpleFinding.getField().equals(finding.getField())
                          && simpleFinding.getType().equals(finding.getType())
                          && simpleFinding.getValue().equals(finding.getValue()))
              .findAny();

      if (alreadyExistingFinding.isPresent()) {
        alreadyExistingFinding.get().getAssets().addAll(simpleFinding.getAssets());
        alreadyExistingFinding.get().getTeams().addAll(simpleFinding.getTeams());
        alreadyExistingFinding.get().getUsers().addAll(simpleFinding.getUsers());
      } else {
        agregatedFindingsToSave.add(simpleFinding);
      }
    }

    // Saving everything by batch
    injectStatusRepositoryHelper.updateInjectStatusWithTraces(
        injectsStatusToSave.stream().toList(), executionTraceToSave.stream().toList());

    // Before Saving the finding, we need to check if it exists or not
    injectStatusRepositoryHelper.saveFindings(agregatedFindingsToSave);
  }

  /**
   * Compute the status using a list of traces. To do that, we count the number of successes,
   * partial, errors and maybe prevented. If we have only success, we're in success. If we only have
   * errors, we're in error. If we only have maybe prevented, we're in maybe prevented. If we have
   * partials and successes, we're in partial. Otherwise, we're in maybe partial prevented
   *
   * @param statuses the list of statuses to compute
   * @return the corresponding status
   */
  public ExecutionStatus computeStatus(List<ExecutionTraceStatus> statuses) {
    ExecutionStatus executionStatus;
    int successCount = 0, errorCount = 0, partialCount = 0, maybePreventedCount = 0;

    // Counting the successes, errors, partials and maybe prevented
    for (ExecutionTraceStatus status : statuses) {
      switch (status) {
        case SUCCESS, WARNING, ASSET_AGENTLESS -> successCount++;
        case PARTIAL -> partialCount++;
        case ERROR, COMMAND_NOT_FOUND, AGENT_INACTIVE -> errorCount++;
        case MAYBE_PREVENTED, MAYBE_PARTIAL_PREVENTED, COMMAND_CANNOT_BE_EXECUTED ->
            maybePreventedCount++;
      }
    }

    // Depending on the results, we deduce a status
    if (successCount > 0 && errorCount == 0 && maybePreventedCount == 0 && partialCount == 0) {
      executionStatus = ExecutionStatus.SUCCESS;
    } else if (errorCount > 0
        && successCount == 0
        && maybePreventedCount == 0
        && partialCount == 0) {
      executionStatus = ExecutionStatus.ERROR;
    } else if (maybePreventedCount > 0
        && successCount == 0
        && errorCount == 0
        && partialCount == 0) {
      executionStatus = ExecutionStatus.MAYBE_PREVENTED;
    } else if (partialCount > 0 && errorCount == 0 && maybePreventedCount == 0
        || successCount > 0) {
      executionStatus = ExecutionStatus.PARTIAL;
    } else {
      executionStatus = ExecutionStatus.MAYBE_PARTIAL_PREVENTED;
    }
    return executionStatus;
  }
}
