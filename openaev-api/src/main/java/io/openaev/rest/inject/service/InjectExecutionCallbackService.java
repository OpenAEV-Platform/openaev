package io.openaev.rest.inject.service;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.rest.helper.queue.executor.BatchExecutionTraceExecutor;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.RabbitmqService;
import io.openaev.service.inject.BatchingInjectStatusService;
import io.openaev.service.queue.BatchQueueService;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;

/**
 * Entry point of the execution callbacks sent by implants and injectors: checks the implant's
 * agent, then publishes the callback to the inject trace queue for batched ingestion.
 */
@RequiredArgsConstructor
@Service
public class InjectExecutionCallbackService {

  private final InjectService injectService;
  private final InjectExecutionService injectExecutionService;
  private final BatchExecutionTraceExecutor batchExecutionTraceExecutor;
  private final BatchingInjectStatusService batchingInjectStatusService;
  private final RabbitmqService rabbitmqService;
  private final OpenAEVConfig openAEVConfig;
  private final ObjectMapper objectMapper;
  private final PreviewFeatureService previewFeatureService;

  // For testing purpose, we add a setter
  @Getter @Setter private BatchQueueService<InjectExecutionCallback> injectTraceQueueService;

  @PostConstruct
  public void init() throws IOException, TimeoutException {
    if (openAEVConfig.getQueueConfig().get("inject-trace") != null) {
      // Initializing the queue for batching the inject execution trace
      injectTraceQueueService =
          rabbitmqService.createBatchQueueService(
              InjectExecutionCallback.class,
              batchExecutionTraceExecutor::handleInjectExecutionCallbackList,
              objectMapper,
              openAEVConfig.getQueueConfig().get("inject-trace"),
              DEFAULT_TENANT_UUID);
      // Share the queue with the batching service so it can requeue delayed callbacks
      batchingInjectStatusService.setInjectTraceQueueService(injectTraceQueueService);
    }
  }

  /**
   * Handles an execution callback sent by an implant ({@code agentId} set) or an injector ({@code
   * agentId} null). The implant's agent is checked synchronously, before the callback is queued, so
   * the implant gets the 403. The callback is then published to the inject trace queue for batched
   * ingestion, or processed right away when the queue is off or legacy ingestion is enabled.
   *
   * @param agentId the agent reported by the implant, or {@code null} for an injector
   * @param injectId the inject the callback is about
   * @param input the execution result
   * @throws ForbiddenException if the agent is not a target of the inject
   */
  public void injectExecutionCallback(
      @Nullable String agentId, String injectId, InjectExecutionInput input) throws IOException {
    if (agentId != null) {
      injectService.resolveInjectTargetingAgent(injectId, agentId);
    }
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.LEGACY_INGESTION_EXECUTION_TRACE)
        && injectTraceQueueService != null) {
      InjectExecutionCallback injectExecutionCallback =
          InjectExecutionCallback.builder()
              .injectExecutionInput(input)
              .agentId(agentId)
              .injectId(injectId)
              .emissionDate(Instant.now().toEpochMilli())
              .build();

      // Publishing the parameters into a queue for later ingestion
      injectTraceQueueService.publish(injectExecutionCallback);
    } else {
      injectExecutionService.handleInjectExecutionCallback(injectId, agentId, input);
    }
  }
}
