package io.openbas.rest.inject.service;

import static io.openbas.utils.ExecutionTraceUtils.convertExecutionAction;
import static io.openbas.utils.ExecutionTraceUtils.convertExecutionStatus;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import io.openbas.aop.lock.Lock;
import io.openbas.aop.lock.LockResourceType;
import io.openbas.database.model.*;
import io.openbas.database.repository.AgentRepository;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.repository.InjectStatusRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.inject.form.InjectExecutionAction;
import io.openbas.rest.inject.form.InjectExecutionInput;
import io.openbas.rest.inject.form.InjectUpdateStatusInput;
import io.openbas.utils.InjectUtils;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class InjectStatusService {

  private final InjectRepository injectRepository;
  private final AgentRepository agentRepository;
  private final InjectService injectService;
  private final InjectUtils injectUtils;
  private final InjectStatusRepository injectStatusRepository;

  public List<InjectStatus> findPendingInjectStatusByType(String injectType) {
    return this.injectStatusRepository.pendingForInjectType(injectType);
  }

  @Transactional(rollbackOn = Exception.class)
  public Inject updateInjectStatus(String injectId, InjectUpdateStatusInput input) {
    Inject inject = injectRepository.findById(injectId).orElseThrow();
    // build status
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setInject(inject);
    injectStatus.setName(ExecutionStatus.valueOf(input.getStatus()));
    // Save status for inject
    inject.setStatus(injectStatus);
    return injectRepository.save(inject);
  }

  public void addStartImplantExecutionTraceByInject(
      String injectId, String agentId, String message, Instant startTime) {
    InjectStatus injectStatus =
        injectStatusRepository.findByInjectId(injectId).orElseThrow(ElementNotFoundException::new);
    Agent agent = agentRepository.findById(agentId).orElseThrow(ElementNotFoundException::new);
    ExecutionTrace trace =
        new ExecutionTrace(
            injectStatus,
            ExecutionTraceStatus.INFO,
            null,
            message,
            ExecutionTraceAction.START,
            agent,
            startTime);
    injectStatus.addTrace(trace);
    injectStatusRepository.save(injectStatus);
  }

  private int getCompleteTrace(Inject inject) {
    return inject.getStatus().map(InjectStatus::getTraces).orElse(Collections.emptyList()).stream()
        .filter(trace -> ExecutionTraceAction.COMPLETE.equals(trace.getAction()))
        .filter(trace -> trace.getAgent() != null)
        .map(trace -> trace.getAgent().getId())
        .distinct()
        .toList()
        .size();
  }

  public boolean isAllInjectAgentsExecuted(Inject inject) {
    int totalCompleteTrace = getCompleteTrace(inject);
    List<Agent> agents = this.injectService.getAgentsByInject(inject);
    return agents.size() == totalCompleteTrace;
  }

  public void updateFinalInjectStatus(InjectStatus injectStatus) {
    ExecutionStatus finalStatus =
        computeStatus(
            injectStatus.getTraces().stream()
                .filter(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
                .toList());
    injectStatus.setTrackingEndDate(Instant.now());
    injectStatus.setName(finalStatus);
    injectStatus.getInject().setUpdatedAt(Instant.now());
  }

  /**
   * Get the execution time from the start trace time and the duration for a specific agent.
   *
   * @param injectStatus the InjectStatus containing the traces
   * @param agentId the ID of the agent to filter the start trace
   * @param durationInMilis the duration in milliseconds to add to the start trace time
   * @return the calculated execution time as an Instant, or the current time if no start trace is
   *     found
   */
  public Instant getExecutionTimeFromStartTraceTimeAndDurationByAgentId(
      InjectStatus injectStatus, String agentId, int durationInMilis) {
    return injectStatus.getTraces().stream()
        .filter(
            trace ->
                trace.getAction() == ExecutionTraceAction.START
                    && agentId.equals(trace.getAgent().getId()))
        .findFirst()
        .map(startTrace -> startTrace.getTime().plusMillis(durationInMilis))
        .orElse(Instant.now());
  }

  public ExecutionTrace createExecutionTrace(
      InjectStatus injectStatus,
      InjectExecutionInput input,
      Agent agent,
      ObjectNode structuredOutput) {

    // We start by computing the trace date. It should be qual to the START execution trace +
    // input.duration.
    // If the duration is 0 or if there is no START execution trace, we use the current time.
    Instant traceCreationTime;

    boolean noTraces = injectStatus.getTraces().isEmpty();
    boolean noDuration = input.getDuration() == 0;

    if (noTraces || noDuration || agent == null) {
      traceCreationTime = Instant.now();
    } else {
      traceCreationTime =
          getExecutionTimeFromStartTraceTimeAndDurationByAgentId(
              injectStatus, agent.getId(), input.getDuration());
    }

    ExecutionTraceAction executionAction = convertExecutionAction(input.getAction());
    ExecutionTraceStatus traceStatus = ExecutionTraceStatus.valueOf(input.getStatus());

    ExecutionTrace base =
        new ExecutionTrace(
            injectStatus,
            traceStatus,
            null,
            input.getMessage(),
            executionAction,
            agent,
            traceCreationTime);
    return ExecutionTrace.from(base, structuredOutput);
  }

  @VisibleForTesting
  protected void computeExecutionTraceStatusIfNeeded(
      InjectStatus injectStatus, ExecutionTrace executionTrace, Agent agent) {

    // if the execution trace is COMPLETED with a status different than INFO we need don't to
    // compute
    if (agent != null
        && executionTrace.getAction().equals(ExecutionTraceAction.COMPLETE)
        && executionTrace.getStatus() != null
        && ExecutionTraceStatus.INFO.equals(executionTrace.getStatus())) {
      ExecutionTraceStatus traceStatus =
          convertExecutionStatus(
              computeStatus(
                  injectStatus.getTraces().stream()
                      .filter(t -> t.getAgent() != null)
                      .filter(t -> t.getAgent().getId().equals(agent.getId()))
                      .toList()));
      executionTrace.setStatus(traceStatus);
    }
  }

  public void updateInjectStatus(
      Agent agent, Inject inject, InjectExecutionInput input, ObjectNode structuredOutput) {
    InjectStatus injectStatus = inject.getStatus().orElseThrow(ElementNotFoundException::new);

    ExecutionTrace executionTrace =
        createExecutionTrace(injectStatus, input, agent, structuredOutput);
    computeExecutionTraceStatusIfNeeded(injectStatus, executionTrace, agent);
    injectStatus.addTrace(executionTrace);

    if (executionTrace.getAction().equals(ExecutionTraceAction.COMPLETE)
        && (agent == null || isAllInjectAgentsExecuted(inject))) {
      updateFinalInjectStatus(injectStatus);
      log.debug("Successfully updated inject final status: " + inject.getId());
    }

    injectRepository.save(inject);
    log.debug("Successfully updated inject: " + inject.getId());
  }

  public ExecutionStatus computeStatus(List<ExecutionTrace> traces) {
    ExecutionStatus executionStatus;
    int successCount = 0, errorCount = 0, partialCount = 0, maybePreventedCount = 0;

    for (ExecutionTrace trace : traces) {
      switch (trace.getStatus()) {
        case SUCCESS, WARNING, ASSET_AGENTLESS -> successCount++;
        case PARTIAL -> partialCount++;
        case ERROR, COMMAND_NOT_FOUND, AGENT_INACTIVE -> errorCount++;
        case MAYBE_PREVENTED, MAYBE_PARTIAL_PREVENTED, COMMAND_CANNOT_BE_EXECUTED ->
            maybePreventedCount++;
      }
    }

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

  public InjectStatus fromExecution(Execution execution, InjectStatus injectStatus) {
    if (!execution.getTraces().isEmpty()) {
      List<ExecutionTrace> traces =
          execution.getTraces().stream().peek(t -> t.setInjectStatus(injectStatus)).toList();
      injectStatus.getTraces().addAll(traces);
    }
    if (execution.isAsync() && ExecutionStatus.EXECUTING.equals(injectStatus.getName())) {
      injectStatus.setName(ExecutionStatus.PENDING);
    } else {
      updateFinalInjectStatus(injectStatus);
    }
    return injectStatus;
  }

  private InjectStatus getOrInitializeInjectStatus(Inject inject) {
    return inject
        .getStatus()
        .orElseGet(
            () -> {
              InjectStatus newStatus = new InjectStatus();
              newStatus.setInject(inject);
              newStatus.setTrackingSentDate(Instant.now());
              return newStatus;
            });
  }

  public InjectStatus failInjectStatus(@NotNull String injectId, @Nullable String message) {
    Inject inject = this.injectRepository.findById(injectId).orElseThrow();
    InjectStatus injectStatus = getOrInitializeInjectStatus(inject);
    if (message != null) {
      injectStatus.addErrorTrace(message, ExecutionTraceAction.COMPLETE);
    }
    injectStatus.setName(ExecutionStatus.ERROR);
    injectStatus.setTrackingEndDate(Instant.now());
    injectStatus.setPayloadOutput(injectUtils.getStatusPayloadFromInject(inject));
    return injectStatusRepository.save(injectStatus);
  }

  @Transactional
  public InjectStatus initializeInjectStatus(
      @NotNull String injectId, @NotNull ExecutionStatus status) {
    Inject inject = this.injectRepository.findById(injectId).orElseThrow();
    InjectStatus injectStatus = getOrInitializeInjectStatus(inject);
    injectStatus.setName(status);
    injectStatus.setTrackingSentDate(Instant.now());
    injectStatus.setPayloadOutput(injectUtils.getStatusPayloadFromInject(inject));
    return injectStatusRepository.save(injectStatus);
  }

  public Iterable<InjectStatus> saveAll(@NotNull List<InjectStatus> injectStatuses) {
    return this.injectStatusRepository.saveAll(injectStatuses);
  }

  @Lock(type = LockResourceType.INJECT, key = "#injectId")
  public void setImplantErrorTrace(String injectId, String agentId, String message) {
    if (injectId != null && !injectId.isBlank() && agentId != null && !agentId.isBlank()) {
      // Create execution traces to inform that the architecture or platform are not compatible with
      // the OpenBAS implant
      Inject inject =
          injectRepository
              .findById(injectId)
              .orElseThrow(() -> new ElementNotFoundException("Inject not found: " + injectId));
      Agent agent =
          agentRepository
              .findById(agentId)
              .orElseThrow(() -> new ElementNotFoundException("Agent not found: " + agentId));
      InjectStatus injectStatus =
          inject.getStatus().orElseThrow(() -> new IllegalArgumentException("Status should exist"));
      injectStatus.addTrace(ExecutionTraceStatus.ERROR, message, ExecutionTraceAction.START, agent);
      injectStatusRepository.save(injectStatus);
      InjectExecutionInput input = new InjectExecutionInput();
      input.setMessage("Execution done");
      input.setStatus(ExecutionTraceStatus.INFO.name());
      input.setAction(InjectExecutionAction.complete);
      this.updateInjectStatus(agent, inject, input, null);
    }
    throw new IllegalArgumentException(message);
  }
}
