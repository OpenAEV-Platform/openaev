package io.openaev.executors;

import static io.openaev.database.model.ExecutionStatus.EXECUTING;
import static io.openaev.utils.InjectionUtils.isInInjectableRange;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.*;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutableInjectDTOMapper;
import io.openaev.execution.ExecutionExecutorService;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.inject.service.ResolvedAssetsToExecute;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.RabbitmqService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
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
  private final OpenAEVConfig openAEVConfig;

  public static final String CMD = "cmd";
  public static final String PSH = "psh";

  private InjectStatus executeExternal(ExecutableInject executableInject, Injector injector)
      throws Exception {
    Inject inject = executableInject.getInjection().getInject();
    String authorisationCode = injectService.getAuthorisationCodeIfNeeded(executableInject);
    String jsonInject =
        mapper.writeValueAsString(
            executableInjectDTOMapper.toExecutableInjectDTO(executableInject, authorisationCode));
    InjectStatus injectStatus =
        this.injectStatusRepository.findByInjectId(inject.getId()).orElseThrow();

    injectExpectationService.computeAndSaveExpectations(executableInject, injector.getType());

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              try {
                rabbitmqService.publish(injector.getId(), jsonInject);
              } catch (IOException | TimeoutException e) {
                throw new IllegalStateException("Failed to publish inject after commit", e);
              }
            }
          });
    } else {
      rabbitmqService.publish(injector.getId(), jsonInject);
    }

    injectStatus.addInfoTrace(
        "The inject has been published and is now waiting to be consumed.",
        ExecutionTraceAction.EXECUTION);
    return this.injectStatusRepository.save(injectStatus);
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

  public InjectStatus execute(ExecutableInject executableInject) throws Exception {
    Inject inject = executableInject.getInjection().getInject();
    InjectorContract injectorContract =
        inject
            .getInjectorContract()
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

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
                      new IllegalStateException(
                          "Injector not found for type: " + inject.getType()));
    }

    // Telemetry - We shouldn't have multiple injector type per executable inject but if it happens,
    // we might as well cover that case
    actionMetricCollector.addInjectPlayedCount(injector.getType());

    boolean hasStartedConnectorInstanceForInjector =
        this.connectorInstanceService.hasStartedConnectorInstanceForInjector(injector.getId());
    if (!hasStartedConnectorInstanceForInjector) {
      throw new IllegalStateException(
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
      return executeExternal(executableInject, injector);
    } else {
      return executeInternal(executableInject, injector);
    }
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
      throw new UnsupportedOperationException("Inject is empty");
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
