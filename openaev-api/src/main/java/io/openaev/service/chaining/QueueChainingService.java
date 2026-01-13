package io.openaev.service.chaining;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.RabbitmqConfig;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.rest.helper.queue.BatchQueueService;
import io.openaev.rest.helper.queue.DelayQueueService;
import io.openaev.rest.helper.queue.QueueExecution;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueueChainingService {

  private final RabbitmqConfig rabbitmqConfig;
  private final OpenAEVConfig openAEVConfig;
  private final ObjectMapper objectMapper;

  @Setter
  private DelayQueueService<StepEvent> delayQueueService;
  @Setter
  private BatchQueueService<StepEvent> waitQueueService;
  @Setter
  private BatchQueueService<ExternalUpdateEvent> updateQueueService;

  // Queues startup
  @PostConstruct
  public void init() throws IOException, TimeoutException {
    if (openAEVConfig.getQueueConfig().get("workflows-wait") == null
      || openAEVConfig.getQueueConfig().get("workflows-update") == null
      || openAEVConfig.getQueueConfig().get("workflows-delay") == null) {
      // TODO: better message
      throw new RuntimeException("workflows-wait, workflows-update and workflows-delay configuration not set. Please refer to the documentation");
    }
    // Initializing the queue to manage tasks to schedule
    waitQueueService =
      new BatchQueueService<>(
        StepEvent.class,
        this::handleWaitEvent,
        rabbitmqConfig,
        objectMapper,
        openAEVConfig.getQueueConfig().get("workflows-wait"));

    // Initializing the queue to manage tasks blocked by a time condition
    delayQueueService =
      new DelayQueueService<>(
        StepEvent.class,
        this::handleDelayEvent,
        rabbitmqConfig,
        objectMapper,
        openAEVConfig.getQueueConfig().get("workflows-delay"));

    // Initializing the queue to manage update event from external sources
    updateQueueService =
      new BatchQueueService<>(
        ExternalUpdateEvent.class,
        this::handleUpdateEvent,
        rabbitmqConfig,
        objectMapper,
        openAEVConfig.getQueueConfig().get("workflows-update"));
  }

  public void delayStep(
    Step stepTemplate,
    Workflow workflowRun,
    long delayMs) throws IOException {
    log.info(
      "PUBLISH STEP DELAY : {} CONDITION TIME: {} + {} milliseconds",
      stepTemplate.getId(),
      workflowRun.getWorkflowCreatedAt(),
      delayMs);
    StepEvent event = new StepEvent();
    event.setStepId(stepTemplate.getId());
    event.setWorkflowId(workflowRun.getId());
    event.setEmissionDate(Instant.now().toEpochMilli());
    delayQueueService.publish(event, delayMs);
  }

  public void waitStep(
    Step stepExecution,
    Workflow workflowRun) throws IOException {
    log.info("PUBLISH STEP WAIT : {}", stepExecution.getId());
    StepEvent event = new StepEvent();
    event.setStepId(stepExecution.getId());
    event.setWorkflowId(workflowRun.getId());
    event.setEmissionDate(Instant.now().toEpochMilli());
    waitQueueService.publish(event);
  }

  public void updateStep(String stepRunId) throws IOException {
    log.info("PUBLISH STEP UPDATE : {}", stepRunId);
    ExternalUpdateEvent event = new ExternalUpdateEvent();
    event.setStepId(stepRunId);
    event.setEmissionDate(Instant.now().toEpochMilli());
    updateQueueService.publish(event);
  }

  // TODO ugly workaround for circular dependencies, to fix
  public void setCallbackForDelayQueue(QueueExecution<StepEvent> callback) {
    delayQueueService.setQueueExecution(callback);
  }

  public void setCallbackForWaitQueue(QueueExecution<StepEvent> callback) {
    waitQueueService.setQueueExecution(callback);
  }

  public void setCallbackForExternalUpdateQueue(QueueExecution<ExternalUpdateEvent> callback) {
    updateQueueService.setQueueExecution(callback);
  }

  @Transactional
  public List<StepEvent> handleWaitEvent(List<StepEvent> events) {
    return events;
  }
  
  @Transactional
  public List<StepEvent> handleDelayEvent(List<StepEvent> events) {
    return events;
  }

  @Transactional
  public List<ExternalUpdateEvent> handleUpdateEvent(List<ExternalUpdateEvent> events) {
    return events;
  }
  // TODO END from previous todo
}
