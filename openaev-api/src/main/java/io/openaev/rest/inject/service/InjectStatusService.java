package io.openaev.rest.inject.service;

import static io.openaev.utils.ExecutionTraceUtils.convertExecutionAction;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.aop.lock.Lock;
import io.openaev.aop.lock.LockResourceType;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.helper.ExecutionTraceRepositoryHelper;
import io.openaev.database.model.*;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.EventType;
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
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final Optional<AuditLogger> auditLogger;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper mapper;

  private final EntityManager entityManager;

  /**
   * Streams the parent inject (with its embedded status) to the SSE consumers.
   *
   * <p>Inject status transitions persist only the {@link InjectStatus} entity, which carries no
   * entity listener and no id the frontend can map back to an inject ({@code inject} is
   * {@code @JsonIgnore}d): without this explicit event, the execution screens never see the
   * QUEUING/EXECUTING/PENDING/terminal transitions in real time and the live board only moves on a
   * full page reload. Must be called within an active transaction: {@link
   * io.openaev.rest.stream.StreamApi} listens with {@code @TransactionalEventListener} and delivers
   * after commit.
   *
   * @param inject the inject whose status just changed, with the fresh status attached
   */
  private void publishInjectStatusUpdate(Inject inject) {
    eventPublisher.publishEvent(new BaseEvent(ModelBaseListener.DATA_UPDATE, inject, mapper));
  }

  public InjectStatus findInjectStatusByInjectId(final String injectId) {
    return findInjectStatusByInjectIdOptional(injectId)
        .orElseThrow(
            () -> new ElementNotFoundException("Inject status not found for :" + injectId));
  }

  public Optional<InjectStatus> findInjectStatusByInjectIdOptional(final String injectId) {
    if (!hasText(injectId)) {
      throw new IllegalArgumentException("InjectId should not be null");
    }
    return this.injectStatusRepository.findByInjectId(injectId);
  }

  @Transactional(rollbackFor = Exception.class)
  public Inject updateInjectStatus(String injectId, InjectUpdateStatusInput input) {
    Inject inject = injectRepository.findById(injectId).orElseThrow();
    // build status
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setInject(inject);
    injectStatus.setName(ExecutionStatus.fromName(input.getStatus()));
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

  public void addJobRetrievalTraces(AssetAgentJob job) {
    if (job.getInject() != null && job.getAgent() != null) {
      String injectId = job.getInject().getId();
      injectStatusRepository
          .findByInjectId(injectId)
          .ifPresent(
              status -> {
                ExecutionTraceUtils.addJobRetrievalTrace(status, job.getAgent());
                injectStatusRepository.save(status);
              });
    }
  }

  private int getCompleteTrace(Inject inject) {
    return inject
        .getStatus()
        .map(s -> ExecutionTraceUtils.getCompletedAgentIds(s.getTraces()).size())
        .orElse(0);
  }

  public boolean isAllInjectAgentsExecuted(Inject inject) {
    int totalCompleteTrace = getCompleteTrace(inject);
    // Use the agent count persisted at launch when available: it avoids re-resolving the full
    // asset/agent graph (incl. dynamic asset-group filters) on every COMPLETE callback
    Integer expectedAgentCount =
        inject.getStatus().map(InjectStatus::getExpectedAgentCount).orElse(null);
    if (expectedAgentCount != null) {
      return totalCompleteTrace >= expectedAgentCount;
    }
    // Fallback for injects launched before the expected count was persisted
    List<Agent> agents = this.injectService.getAgentsByInject(inject);
    return agents.size() == totalCompleteTrace;
  }

  public void updateFinalInjectStatus(InjectStatus injectStatus) {
    ExecutionStatus previousStatus = injectStatus.getName();
    ExecutionStatus finalStatus =
        InjectStatusUtils.computeStatus(
            injectStatus.getTraces().stream()
                .filter(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
                .toList());
    injectStatus.setTrackingEndDate(Instant.now());
    injectStatus.setName(finalStatus);
    injectStatus.getInject().setUpdatedAt(Instant.now());

    // Audit log: final inject status
    if (previousStatus != finalStatus) {
      logInjectStatusTransition(injectStatus.getInject(), previousStatus, finalStatus, null);
    }
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
                    // Agent-less START traces exist (global distribution trace): skip them
                    && trace.getAgent() != null
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
    ExecutionTraceStatus traceStatus = ExecutionTraceStatus.fromName(input.getStatus());

    // Injector callbacks (no agent) may scope a trace to one or more targets (assets / AI targets)
    // via execution_context_identifiers. When present the trace becomes target-scoped and surfaces
    // in the per-target execution view; when absent it stays global (agent-less, no identifiers).
    // Agent callbacks are already scoped by their agent: identifiers are ignored for them.
    List<String> contextIdentifiers = agent == null ? input.getContextIdentifiers() : null;

    ExecutionTrace base =
        new ExecutionTrace(
            injectStatus,
            traceStatus,
            contextIdentifiers,
            input.getMessage(),
            executionAction,
            agent,
            traceCreationTime);
    return ExecutionTrace.from(base, structuredOutput);
  }

  public void updateInjectStatus(
      Inject inject, Agent agent, InjectExecutionInput input, ObjectNode structuredOutput) {
    InjectStatus injectStatus = inject.getStatus().orElseThrow(ElementNotFoundException::new);

    // Capture previous status for audit diff logging
    ExecutionStatus previousStatus = injectStatus.getName();

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
      injectRepository.updateUpdatedAt(
          injectStatus.getInject().getId(), injectStatus.getInject().getUpdatedAt());
      executionTraceRepositoryHelper.updateInjectStatus(
          injectStatus.getId(), injectStatus.getName().name(), injectStatus.getTrackingEndDate());
      // updateUpdatedAt is a bulk JPQL update: it bypasses the entity listeners, so no SSE event
      // is emitted. Stream the terminal transition explicitly so the execution board moves the
      // inject from "in flight" to "completed" without a page reload.
      publishInjectStatusUpdate(inject);
      log.debug("Successfully updated inject final status: {}", inject.getId());
    }

    // Audit log: inject status transition
    ExecutionStatus newStatus = injectStatus.getName();
    if (previousStatus != newStatus) {
      logInjectStatusTransition(inject, previousStatus, newStatus, agent);
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

  @Transactional
  public InjectStatus failInjectStatus(@NotNull String injectId, @Nullable String message) {
    Inject inject = this.injectRepository.findById(injectId).orElseThrow();
    InjectStatus injectStatus = getOrInitializeInjectStatus(inject);
    if (message != null) {
      injectStatus.addErrorTrace(message, ExecutionTraceAction.COMPLETE);
    }
    injectStatus.setName(ExecutionStatus.ERROR);
    injectStatus.setTrackingEndDate(Instant.now());
    injectStatus.setPayloadOutput(getPayloadOutput(inject));
    InjectStatus saved = injectStatusRepository.save(injectStatus);
    // Stream the ERROR transition so the execution board moves the inject to "completed" live.
    inject.setStatus(saved);
    publishInjectStatusUpdate(inject);
    return saved;
  }

  @Transactional
  public InjectStatus initializeInjectStatus(
      @NotNull String injectId, @NotNull ExecutionStatus status) {
    Inject inject = this.injectRepository.findById(injectId).orElseThrow();
    InjectStatus injectStatus = getOrInitializeInjectStatus(inject);
    injectStatus.setName(status);
    injectStatus.setTrackingSentDate(Instant.now());
    injectStatus.setPayloadOutput(getPayloadOutput(inject));
    InjectStatus saved = injectStatusRepository.save(injectStatus);
    // Stream the transition (typically -> EXECUTING) so the execution board moves the inject
    // from "up next" to "in flight" without a page reload.
    inject.setStatus(saved);
    publishInjectStatusUpdate(inject);
    return saved;
  }

  public Iterable<InjectStatus> saveAll(@NotNull List<InjectStatus> injectStatuses) {
    return this.injectStatusRepository.saveAll(injectStatuses);
  }

  /**
   * Persists a status transition and streams the parent inject to the SSE consumers, so the
   * execution screens reflect the transition in real time. Used by execution paths that save the
   * status without any listened entity update (internal injector completion, pending-inject timeout
   * finalization).
   *
   * @param injectStatus the inject status to persist
   * @return the persisted status
   */
  @Transactional
  public InjectStatus saveAndStreamInject(@NotNull InjectStatus injectStatus) {
    InjectStatus saved = this.injectStatusRepository.save(injectStatus);
    // Reload the inject inside this transaction: callers typically hold a detached inject (Quartz
    // job threads) whose lazy relations cannot be serialized into the stream event payload.
    Inject inject = this.injectRepository.findById(saved.getInject().getId()).orElseThrow();
    inject.setStatus(saved);
    publishInjectStatusUpdate(inject);
    return saved;
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

  // -- AUDIT HELPERS --

  private void logInjectStatusTransition(
      Inject inject, ExecutionStatus previousStatus, ExecutionStatus newStatus, Agent agent) {
    auditLogger.ifPresent(
        logger ->
            logger.logEvent(
                AuditEvent.builder()
                    .eventType(EventType.EXECUTION)
                    .eventScope(AuditEventScope.INJECT_STATUS_TRANSITION)
                    .eventStatus(EventStatus.SUCCESS)
                    .resourceType("Inject")
                    .resourceId(inject.getId())
                    .message(
                        "Inject '%s' transitioned from %s to %s"
                            .formatted(inject.getTitle(), previousStatus.name(), newStatus.name()))
                    .contextData(
                        Map.of(
                            "inject_id", inject.getId(),
                            "inject_name", inject.getTitle(),
                            "previous_status", previousStatus.name(),
                            "new_status", newStatus.name(),
                            "executor_type",
                                agent != null && agent.getExecutor() != null
                                    ? agent.getExecutor().getType()
                                    : "openaev"))
                    .origin(AuditEventOrigin.SYSTEM)
                    .build()));
  }
}
