package io.openaev.service.chaining;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.service.RabbitmqService;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.service.queue.QueueExecution;
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

  private final RabbitmqService rabbitmqService;
  private final OpenAEVConfig openAEVConfig;
  private final ObjectMapper objectMapper;

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

    // Initializing the queue to manage tasks to schedule
    readyQueueService =
        rabbitmqService.createBatchQueueService(
            StepEvent.class,
            null,
            objectMapper,
            openAEVConfig.getQueueConfig().get("workflows-ready"));

    // Initializing the queue to manage update event from external sources
    updateQueueService =
        rabbitmqService.createBatchQueueService(
            ExternalUpdateEvent.class,
            null,
            objectMapper,
            openAEVConfig.getQueueConfig().get("workflows-update"));
  }

  @PreDestroy
  public void destroy() throws IOException, TimeoutException {
    if (readyQueueService != null) {
      readyQueueService.stop();
    }

    if (updateQueueService != null) {
      updateQueueService.stop();
    }
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
