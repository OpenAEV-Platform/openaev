package io.openaev.rest.inject.service;

import static io.openaev.utils.ExecutionTraceUtils.convertExecutionAction;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.lock.Lock;
import io.openaev.aop.lock.LockResourceType;
import io.openaev.database.helper.ExecutionTraceRepositoryHelper;
import io.openaev.database.model.*;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.form.InjectUpdateStatusInput;
import io.openaev.utils.ExecutionTraceUtils;
import io.openaev.utils.InjectStatusUtils;
import io.openaev.utils.InjectUtils;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
  private final ExecutionTraceRepositoryHelper executionTraceRepositoryHelper;

  private final EntityManager entityManager;

  public InjectStatus findInjectStatusByInjectId(final String injectId) {
    if (!hasText(injectId)) {
      throw new IllegalArgumentException("InjectId should not be null");
    }
    return this.injectStatusRepository
        .findByInjectId(injectId)
        .orElseThrow(
            () -> new ElementNotFoundException("Inject status not found for :" + injectId));
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

  public void addJobRetrievalTraces(List<AssetAgentJob> jobs) {
    Map<String, List<AssetAgentJob>> jobsByInjectId =
        jobs.stream()
            .filter(j -> j.getInject() != null && j.getAgent() != null)
            .collect(Collectors.groupingBy(j -> j.getInject().getId()));
    if (jobsByInjectId.isEmpty()) {
      return;
    }
    List<InjectStatus> statuses =
        injectStatusRepository.findAllByInjectIdIn(jobsByInjectId.keySet());
    for (InjectStatus status : statuses) {
      for (AssetAgentJob job : jobsByInjectId.getOrDefault(status.getInject().getId(), List.of())) {
        ExecutionTraceUtils.addJobRetrievalTrace(status, job.getAgent());
      }
    }
    injectStatusRepository.saveAll(statuses);
  }

  private int getCompleteTrace(Inject inject) {
    return inject
        .getStatus()
        .map(s -> ExecutionTraceUtils.getCompletedAgentIds(s.getTraces()).size())
        .orElse(0);
  }

  public boolean isAllInjectAgentsExecuted(Inject inject) {
    int totalCompleteTrace = getCompleteTrace(inject);
    List<Agent> agents = this.injectService.getAgentsByInject(inject);
    return agents.size() == totalCompleteTrace;
  }

  public void updateFinalInjectStatus(InjectStatus injectStatus) {
    ExecutionStatus finalStatus =
        InjectStatusUtils.computeStatus(
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

  public void updateInjectStatus(
      Inject inject, Agent agent, InjectExecutionInput input, ObjectNode structuredOutput) {
    InjectStatus injectStatus = inject.getStatus().orElseThrow(ElementNotFoundException::new);

    // Creating the Execution Trace
    ExecutionTrace executionTrace =
        createExecutionTrace(injectStatus, input, agent, structuredOutput);
    // Resolve the placeholder status of the COMPLETE trace
    resolveCompleteTraceStatus(injectStatus, executionTrace, agent);
    injectStatus.addTrace(executionTrace);
    // Save the trace using a low level call to the database
    String executionTraceId = executionTraceRepositoryHelper.saveExecutionTrace(executionTrace);
    executionTrace.setId(executionTraceId);
    entityManager.merge(injectStatus);

    // If the trace is complete
    if (executionTrace.getAction().equals(ExecutionTraceAction.COMPLETE)
        && (agent == null || isAllInjectAgentsExecuted(inject))) {
      // We update the status of the inject
      updateFinalInjectStatus(injectStatus);
      executionTraceRepositoryHelper.updateInjectUpdateDate(
          injectStatus.getInject().getId(), injectStatus.getInject().getUpdatedAt());
      executionTraceRepositoryHelper.updateInjectStatus(
          injectStatus.getId(), injectStatus.getName().name(), injectStatus.getTrackingEndDate());
      log.debug("Successfully updated inject final status: {}", inject.getId());
    }

    log.debug("Successfully updated inject: {}", inject.getId());
  }

  /**
   * Resolves the status of a COMPLETE trace when the implant sent the default INFO placeholder. The
   * real status is computed from the agent's previous traces (prerequisite, execution, cleanup). If
   * the implant sent an explicit status (not INFO), it is kept as-is.
   */
  protected void resolveCompleteTraceStatus(
      InjectStatus injectStatus, ExecutionTrace executionTrace, Agent agent) {

    if (agent == null
        || !ExecutionTraceAction.COMPLETE.equals(executionTrace.getAction())
        || !ExecutionTraceStatus.INFO.equals(executionTrace.getStatus())) {
      return;
    }

    ExecutionTraceStatus computedStatus =
        ExecutionTraceUtils.computeAgentTraceStatus(
            injectStatus.getTraces().stream()
                .filter(t -> t.getAgent() != null)
                .filter(t -> t.getAgent().getId().equals(agent.getId()))
                .toList());

    if (computedStatus != null) {
      executionTrace.setStatus(computedStatus);
    }
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

  private StatusPayload getPayloadOutput(Inject inject) {
    return injectUtils.getStatusPayloadFromInject(inject);
  }

  public InjectStatus failInjectStatus(@NotNull String injectId, @Nullable String message) {
    Inject inject = this.injectRepository.findById(injectId).orElseThrow();
    InjectStatus injectStatus = getOrInitializeInjectStatus(inject);
    if (message != null) {
      injectStatus.addErrorTrace(message, ExecutionTraceAction.COMPLETE);
    }
    injectStatus.setName(ExecutionStatus.ERROR);
    injectStatus.setTrackingEndDate(Instant.now());
    injectStatus.setPayloadOutput(getPayloadOutput(inject));
    return injectStatusRepository.save(injectStatus);
  }

  @Transactional
  public InjectStatus initializeInjectStatus(
      @NotNull String injectId, @NotNull ExecutionStatus status) {
    Inject inject = this.injectRepository.findById(injectId).orElseThrow();
    InjectStatus injectStatus = getOrInitializeInjectStatus(inject);
    injectStatus.setName(status);
    injectStatus.setTrackingSentDate(Instant.now());
    injectStatus.setPayloadOutput(getPayloadOutput(inject));
    return injectStatusRepository.save(injectStatus);
  }

  public Iterable<InjectStatus> saveAll(@NotNull List<InjectStatus> injectStatuses) {
    return this.injectStatusRepository.saveAll(injectStatuses);
  }

  public InjectStatus save(@NotNull InjectStatus injectStatus) {
    return this.injectStatusRepository.save(injectStatus);
  }

  @Lock(type = LockResourceType.INJECT, key = "#injectId")
  public void setImplantErrorTrace(String injectId, String agentId, String message) {
    if (injectId != null && !injectId.isBlank() && agentId != null && !agentId.isBlank()) {
      // Create execution traces to inform that the architecture or platform are not compatible with
      // the OpenAEV implant
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
      this.updateInjectStatus(inject, agent, input, null);
    }
  }

  /**
   * Delete all injects statuses for a list of injects
   *
   * @param injects the list of injects
   */
  public void deleteAllInjectStatusByInjects(List<Inject> injects) {
    List<String> injectStatusIds =
        injects.stream()
            .map(Inject::getStatus)
            .flatMap(i -> i.map(InjectStatus::getId).stream())
            .toList();
    injectStatusRepository.deleteAllByIds(injectStatusIds);
  }
}
