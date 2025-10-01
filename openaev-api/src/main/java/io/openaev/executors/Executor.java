package io.openaev.executors;

import static io.openaev.database.model.ExecutionStatus.EXECUTING;
import static io.openaev.utils.InjectionUtils.isInInjectableRange;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.asset.QueueService;
import io.openaev.database.model.*;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionExecutorService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class Executor {

  @Resource protected ObjectMapper mapper;

  private final ApplicationContext context;

  private final InjectStatusRepository injectStatusRepository;
  private final InjectorRepository injectorRepository;

  private final QueueService queueService;
  private final ActionMetricCollector actionMetricCollector;

  private final ExecutionExecutorService executionExecutorService;
  private final InjectStatusService injectStatusService;

  private InjectStatus executeExternal(ExecutableInject executableInject, Injector injector)
      throws IOException, TimeoutException {
    Inject inject = executableInject.getInjection().getInject();
    String jsonInject = mapper.writeValueAsString(executableInject);
    InjectStatus injectStatus =
        this.injectStatusRepository.findByInjectId(inject.getId()).orElseThrow();
    queueService.publish(injector.getType(), jsonInject);
    injectStatus.addInfoTrace(
        "The inject has been published and is now waiting to be consumed.",
        ExecutionTraceAction.EXECUTION);
    return this.injectStatusRepository.save(injectStatus);
  }

  private InjectStatus executeInternal(ExecutableInject executableInject, Injector injector) {
    Inject inject = executableInject.getInjection().getInject();
    io.openaev.executors.Injector executor =
        this.context.getBean(injector.getType(), io.openaev.executors.Injector.class);
    Execution execution = executor.executeInjection(executableInject);
    // After execution, expectations are already created
    // Injection status is filled after complete execution
    // Report inject execution
    InjectStatus injectStatus =
        this.injectStatusRepository.findByInjectId(inject.getId()).orElseThrow();
    InjectStatus completeStatus = injectStatusService.fromExecution(execution, injectStatus);
    return injectStatusRepository.save(completeStatus);
  }

  public InjectStatus execute(ExecutableInject executableInject)
      throws IOException, TimeoutException {
    Inject inject = executableInject.getInjection().getInject();
    InjectorContract injectorContract =
        inject
            .getInjectorContract()
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

    // Telemetry
    actionMetricCollector.addInjectPlayedCount(injectorContract.getInjector().getType());

    // Depending on injector type (internal or external) execution must be done differently
    Injector injector =
        injectorRepository
            .findByType(injectorContract.getInjector().getType())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Injector not found for type: "
                            + injectorContract.getInjector().getType()));

    // Status
    InjectStatus updatedStatus =
        this.injectStatusService.initializeInjectStatus(inject.getId(), EXECUTING);
    inject.setStatus(updatedStatus);
    if (Boolean.TRUE.equals(injectorContract.getNeedsExecutor())) {
      this.executionExecutorService.launchExecutorContext(inject);
    }
    if (injector.isExternal()) {
      return executeExternal(executableInject, injector);
    } else {
      return executeInternal(executableInject, injector);
    }
  }

  public InjectStatus directExecute(ExecutableInject executableInject)
      throws IOException, TimeoutException {
    boolean isScheduledInject = !executableInject.isDirect();
    // If empty content, inject must be rejected
    Inject inject = executableInject.getInjection().getInject();
    if (inject.getContent() == null) {
      throw new UnsupportedOperationException("Inject is empty");
    }
    // If inject is too old, reject the execution
    if (isScheduledInject && !isInInjectableRange(inject)) {
      throw new UnsupportedOperationException(
          "Inject is now too old for execution: id "
              + inject.getId()
              + ", launch date "
              + inject.getDate()
              + ", now date "
              + Instant.now());
    }

    return this.execute(executableInject);
  }
}
