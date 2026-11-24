package io.openaev.executors;

import static io.openaev.database.model.ExecutionStatus.EXECUTING;
import static io.openaev.utils.InjectionUtils.isInInjectableRange;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.helper.ExecutionTraceRepositoryHelper;
import io.openaev.database.model.*;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutableInjectDTOMapper;
import io.openaev.execution.ExecutionExecutorService;
import io.openaev.execution.UnexecutableInjectException;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.inject.service.ResolvedAssetsToExecute;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.RabbitmqService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.payload.PayloadRevocationService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class Executor {

  @Resource protected ObjectMapper mapper;

  private final InjectStatusRepository injectStatusRepository;
  private final InjectorRepository injectorRepository;

  private final RabbitmqService rabbitmqService;
  private final ActionMetricCollector actionMetricCollector;
  private final ManagerFactory managerFactory;

  private final ExecutionExecutorService executionExecutorService;
  private final InjectStatusService injectStatusService;
  private final InjectService injectService;
  private final ExecutableInjectDTOMapper executableInjectDTOMapper;
  private final ConnectorInstanceService connectorInstanceService;
  private final InjectExpectationService injectExpectationService;
  private final PayloadRevocationService payloadRevocationService;
  private final OpenAEVConfig openAEVConfig;
  private final ExecutionTraceRepositoryHelper executionTraceRepositoryHelper;

  public static final String CMD = "cmd";
  public static final String PSH = "psh";

  private static final String PUBLISHED_TRACE =
      "The inject has been published and is now waiting to be consumed.";

  /**
   * What phase 1 (DB prep) of an execution produced. An internal injector finishes synchronously,
   * so {@link #completedStatus()} already carries its final status. An external injector stops
   * right after {@code computeAndSaveExpectations}: {@link #pending()} carries what the publish
   * (phase 2, no transaction) and the outcome trace (phase 3, a transaction of its own) need, so a
   * caller that owns its transaction boundary can commit phase 1 and release the connection before
   * the network call. See #6968.
   */
  public record ExecutionPhase1Result(InjectStatus completedStatus, ExternalPublish pending) {
    public static ExecutionPhase1Result completed(InjectStatus status) {
      return new ExecutionPhase1Result(status, null);
    }

    public static ExecutionPhase1Result pendingPublish(ExternalPublish pending) {
      return new ExecutionPhase1Result(null, pending);
    }

    public boolean isPendingPublish() {
      return pending != null;
    }
  }

  /** What a confirmed or failed publish needs, carried across the no-transaction publish step. */
  public record ExternalPublish(
      String injectId, String injectStatusId, String injectorId, String jsonInject) {}

  /**
   * Phase 1 of the external path: DB prep up to {@code computeAndSaveExpectations}, ending before
   * the publish. Runs inside the caller's current transaction; opens none itself.
   */
  private ExternalPublish prepareExternalPublish(
      ExecutableInject executableInject, Injector injector) throws Exception {
    Inject inject = executableInject.getInjection().getInject();
    String authorisationCode = injectService.getAuthorisationCodeIfNeeded(executableInject);
    String jsonInject =
        mapper.writeValueAsString(
            executableInjectDTOMapper.toExecutableInjectDTO(executableInject, authorisationCode));
    InjectStatus injectStatus =
        this.injectStatusRepository.findByInjectId(inject.getId()).orElseThrow();

    injectExpectationService.computeAndSaveExpectations(executableInject, injector.getType());

    return new ExternalPublish(inject.getId(), injectStatus.getId(), injector.getId(), jsonInject);
  }

  /**
   * Phase 2 of the external path: the network publish. Deliberately opens no transaction and
   * expects none to be active; see {@link ExecutionPhase1Result}.
   */
  public void publish(ExternalPublish pending) throws IOException, TimeoutException {
    rabbitmqService.publish(pending.injectorId(), pending.jsonInject());
  }

  /**
   * How many publishes a batch may have in flight. Half the publisher pool, which hands a publish
   * over with no queue: the other half absorbs the hand-off lag and the threads a timed-out publish
   * leaves busy, so a large batch waits for a slot instead of being rejected.
   */
  public int maxConcurrentPublishes() {
    return Math.max(1, rabbitmqService.getPublishThreads() / 2);
  }

  /**
   * Phase 3 of the external path, confirmed publish: inserts the "published and waiting" trace row.
   * The inject status is neither rewritten nor streamed: the injector's callback may have completed
   * the inject since phase 1, and a write or an event built from the status read here could undo
   * that completion. Needs an active transaction.
   */
  public void recordPublishSuccess(ExternalPublish pending) {
    InjectStatus injectStatus =
        this.injectStatusRepository.findById(pending.injectStatusId()).orElseThrow();
    this.executionTraceRepositoryHelper.saveExecutionTrace(
        new ExecutionTrace(
            injectStatus,
            ExecutionTraceStatus.INFO,
            List.of(),
            PUBLISHED_TRACE,
            ExecutionTraceAction.EXECUTION,
            null,
            null));
  }

  /**
   * Phase 3 of the external path, failed publish: records the real failure instead of a trace that
   * claims a publish which never happened. Needs an active transaction.
   */
  public InjectStatus recordPublishFailure(ExternalPublish pending, Exception cause) {
    return this.injectStatusService.persistErrorStatusInTransaction(
        pending.injectId(), failureMessage(cause));
  }

  /** A publish timeout can carry no message: name the exception type so the trace explains it. */
  public static String failureMessage(Throwable cause) {
    String message = cause.getMessage();
    return message == null || message.isBlank()
        ? "Failed to publish the inject: " + cause.getClass().getSimpleName()
        : message;
  }

  private InjectStatus executeInternal(ExecutableInject executableInject, Injector injector) {
    Inject inject = executableInject.getInjection().getInject();
    io.openaev.executors.Injector executor =
        managerFactory
            .getManager(inject.getTenant().getId())
            .requestInjectorExecutorByType(injector.getType());

    Execution execution = executor.executeInjection(executableInject);
    // After execution, expectations are already created
    // Injection status is filled after complete execution
    // Report inject execution
    InjectStatus injectStatus =
        this.injectStatusRepository.findByInjectId(inject.getId()).orElseThrow();
    InjectStatus completeStatus = injectStatusService.fromExecution(execution, injectStatus);
    // Save + stream: internal injectors reach their terminal status here without any listened
    // entity update, so the execution board would otherwise only move on a page reload.
    return injectStatusService.saveAndStreamInject(completeStatus);
  }

  /**
   * All DB prep for one execution, ending at {@code computeAndSaveExpectations} for an external
   * injector (see {@link ExecutionPhase1Result}) or at the final status for an internal one. Runs
   * inside whatever transaction the caller already has open; opens none itself.
   */
  public ExecutionPhase1Result executePhase1(ExecutableInject executableInject) throws Exception {
    Inject inject = executableInject.getInjection().getInject();
    InjectorContract injectorContract =
        inject
            .getInjectorContract()
            .orElseThrow(() -> new UnexecutableInjectException("Inject does not have a contract"));
    payloadRevocationService
        .refusal(inject)
        .ifPresent(
            refusal -> {
              throw new UnexecutableInjectException(refusal);
            });

    // Resolve the injector instance from the inject entity directly
    Injector injector = inject.getInjector();
    if (injector == null) {
      // Fallback for legacy injects that may not have the field populated.
      injector =
          injectorRepository
              .findFirstByContractsCompositeIdIdAndTenantId(
                  injectorContract.getId(), inject.getTenant().getId())
              .orElseThrow(
                  () ->
                      new UnexecutableInjectException(
                          "No injector is registered for the injector contract "
                              + injectorContract.getId()));
    }

    // Telemetry - We shouldn't have multiple injector type per executable inject but if it happens,
    // we might as well cover that case
    actionMetricCollector.addInjectPlayedCount(injector.getType());

    boolean hasStartedConnectorInstanceForInjector =
        this.connectorInstanceService.hasStartedConnectorInstanceForInjector(injector.getId());
    if (!hasStartedConnectorInstanceForInjector) {
      throw new UnexecutableInjectException(
          "No started connector instance found for injector type: " + injector.getType());
    }

    // Status
    InjectStatus updatedStatus =
        this.injectStatusService.initializeInjectStatus(inject.getId(), EXECUTING);
    inject.setStatus(updatedStatus);
    ResolvedAssetsToExecute resolvedAssets = resolvedAssetsToExecute(executableInject, inject);
    if (Boolean.TRUE.equals(injectorContract.getNeedsExecutor())) {
      this.executionExecutorService.launchExecutorContext(inject, resolvedAssets);
    } else {
      // Without agent routing, the restricted targets are only dropped by the target resolution:
      // trace them here, once per dispatch
      this.executionExecutorService.saveAssetsWithoutClearanceTraces(
          resolvedAssets.withoutClearance(), updatedStatus);
    }
    if (injector.isExternal()) {
      return ExecutionPhase1Result.pendingPublish(
          prepareExternalPublish(executableInject, injector));
    } else {
      return ExecutionPhase1Result.completed(executeInternal(executableInject, injector));
    }
  }

  /**
   * All three phases in one call, for the manual launch and the chaining step: the behaviour this
   * method had before #6968. Inside the caller's transaction the trace is written with it and the
   * publish waits for {@code afterCommit()}; with none, the publish is inline.
   */
  public InjectStatus execute(ExecutableInject executableInject) throws Exception {
    ExecutionPhase1Result phase1 = executePhase1(executableInject);
    if (!phase1.isPendingPublish()) {
      return phase1.completedStatus();
    }
    ExternalPublish pending = phase1.pending();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              try {
                publish(pending);
              } catch (IOException | TimeoutException e) {
                throw new IllegalStateException("Failed to publish inject after commit", e);
              }
            }
          });
    } else {
      publish(pending);
    }
    InjectStatus injectStatus =
        this.injectStatusRepository.findById(pending.injectStatusId()).orElseThrow();
    injectStatus.addInfoTrace(PUBLISHED_TRACE, ExecutionTraceAction.EXECUTION);
    return this.injectStatusRepository.save(injectStatus);
  }

  /**
   * The targets of this dispatch split by marking clearance, resolved once: reused when the caller
   * already cached them (scheduled execution), resolved and cached otherwise (direct execution), so
   * the executor context, the DTO mapper and the expectation behaviors share the same resolution.
   */
  private ResolvedAssetsToExecute resolvedAssetsToExecute(
      ExecutableInject executableInject, Inject inject) {
    if (executableInject.getResolvedAssetsToExecute() == null) {
      executableInject.cacheAssetsToExecute(
          this.injectService.resolveAssetsToExecuteByClearance(inject));
    }
    return executableInject.getResolvedAssetsToExecute();
  }

  public InjectStatus directExecute(ExecutableInject executableInject) throws Exception {
    boolean isScheduledInject = !executableInject.isDirect();
    // If empty content, inject must be rejected
    Inject inject = executableInject.getInjection().getInject();
    if (inject.getContent() == null) {
      throw new UnexecutableInjectException("Inject is empty");
    }
    // If inject is too old, reject the execution
    if (isScheduledInject
        && !isInInjectableRange(inject, openAEVConfig.getInjectStalenessThreshold())) {
      throw new UnsupportedOperationException(
          "Inject is now too old for execution: id "
              + inject.getId()
              + ", launch date "
              + inject.getDate(Instant.now())
              + ", now date "
              + Instant.now());
    }

    return this.execute(executableInject);
  }
}
