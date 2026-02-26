package io.openaev.service.chaining;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.RabbitMQSslConfiguration;
import io.openaev.config.RabbitmqConfig;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.rest.helper.queue.BatchQueueService;
import io.openaev.rest.helper.queue.QueueExecution;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Service managing the queue system used for the chaining */
@Slf4j
@RequiredArgsConstructor
@Service
public class QueueChainingService {

  private final RabbitmqConfig rabbitmqConfig;
  private final OpenAEVConfig openAEVConfig;
  private final ObjectMapper objectMapper;
  private final RabbitMQSslConfiguration rabbitMQSslConfiguration;

  @Setter private BatchQueueService<StepEvent> delayQueueService; // TODO switch to DB queue
  @Setter private BatchQueueService<StepEvent> readyQueueService;
  @Setter private BatchQueueService<ExternalUpdateEvent> updateQueueService;

  /**
   * Configure the queue at startup
   *
   * @throws IOException in case there is an error while creating the queues
   * @throws TimeoutException in case the configuration for the queue isn't there
   */
  @PostConstruct
  public void init() throws IOException, TimeoutException {
    if (openAEVConfig.getQueueConfig().get("workflows-ready") == null) {
      throw new RuntimeException(
          "workflows-ready configuration is missing. Please refer to the documentation");
    }

    if (openAEVConfig.getQueueConfig().get("workflows-update") == null) {
      throw new RuntimeException(
          "workflows-update configuration is missing. Please refer to the documentation");
    }

    if (openAEVConfig.getQueueConfig().get("workflows-delay") == null) {
      throw new RuntimeException(
          "workflows-delay configuration is missing. Please refer to the documentation");
    }

    // Initializing the queue to manage tasks to schedule
    readyQueueService =
        new BatchQueueService<>(
            StepEvent.class,
            null,
            rabbitmqConfig,
            objectMapper,
            openAEVConfig.getQueueConfig().get("workflows-ready"),
            rabbitMQSslConfiguration);

    // Initializing the queue to manage tasks blocked by a time condition
    delayQueueService =
        new BatchQueueService<>(
            StepEvent.class,
            null,
            rabbitmqConfig,
            objectMapper,
            openAEVConfig.getQueueConfig().get("workflows-delay"),
            rabbitMQSslConfiguration);

    // Initializing the queue to manage update event from external sources
    updateQueueService =
        new BatchQueueService<>(
            ExternalUpdateEvent.class,
            null,
            rabbitmqConfig,
            objectMapper,
            openAEVConfig.getQueueConfig().get("workflows-update"),
            rabbitMQSslConfiguration);
  }

  @PreDestroy
  public void destroy() throws IOException, TimeoutException {
    if (readyQueueService != null) {
      readyQueueService.stop();
    }
    if (delayQueueService != null) {
      delayQueueService.stop();
    }
    if (updateQueueService != null) {
      updateQueueService.stop();
    }
  }

  /**
   * Send a delay event in the delay queue for a given step template
   *
   * @param stepTemplate the step template to delay
   * @param workflowRun the workflow associated with the run of the step
   * @param delayMs the time, in milliseconds, to delay the step
   * @throws IOException in case there is an error while sending the event
   */
  public void delayStep(Step stepTemplate, Workflow workflowRun, long delayMs) throws IOException {
    log.info(
        "PUBLISH STEP DELAY : {} CONDITION TIME: {} + {} milliseconds",
        stepTemplate.getId(),
        workflowRun.getWorkflowCreatedAt(),
        delayMs);
    StepEvent event = new StepEvent();
    event.setStepId(stepTemplate.getId());
    event.setWorkflowId(workflowRun.getId());
    event.setEmissionDate(Instant.now().toEpochMilli());
    delayQueueService.publish(event);
  }

  /**
   * Send a ready event in the ready queue for a given step execution
   *
   * @param stepExecution the step execution to set to ready
   * @param workflowRun the workflow associated with the run of the step
   * @throws IOException in case there is an error while sending the event
   */
  public void readyStep(Step stepExecution, Workflow workflowRun) throws IOException {
    log.info("PUBLISH STEP READY : {}", stepExecution.getId());
    StepEvent event = new StepEvent();
    event.setStepId(stepExecution.getId());
    event.setWorkflowId(workflowRun.getId());
    event.setEmissionDate(Instant.now().toEpochMilli());
    readyQueueService.publish(event);
  }

  /**
   * Send an external update event in the update queue for a given step
   *
   * @param stepRunId the step execution to update
   * @throws IOException in case there is an error while sending the event
   */
  public void updateStep(String stepRunId) throws IOException {
    log.info("PUBLISH STEP UPDATE : {}", stepRunId);
    ExternalUpdateEvent event = new ExternalUpdateEvent();
    event.setStepId(stepRunId);
    event.setEmissionDate(Instant.now().toEpochMilli());
    updateQueueService.publish(event);
  }

  /**
   * Dynamically set a callback function for the delay queue
   *
   * @param callback function to call when receiving an event
   */
  public void setCallbackForDelayQueue(QueueExecution<StepEvent> callback) {
    delayQueueService.setQueueExecution(callback);
  }

  /**
   * Dynamically set a callback function for the ready queue
   *
   * @param callback function to call when receiving an event
   */
  public void setCallbackForReadyQueue(QueueExecution<StepEvent> callback) {
    readyQueueService.setQueueExecution(callback);
  }

  /**
   * Dynamically set a callback function for the external update queue
   *
   * @param callback function to call when receiving an event
   */
  public void setCallbackForExternalUpdateQueue(QueueExecution<ExternalUpdateEvent> callback) {
    updateQueueService.setQueueExecution(callback);
  }
}
