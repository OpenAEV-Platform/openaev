package io.openaev.service.chaining;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Registers callback handlers for the queue chaining service.
 *
 * <p>This component initializes the queue chaining service by registering the appropriate callback
 * methods from the step service for handling different types of queue events.
 */
@Component
@AllArgsConstructor
public class QueueChainingServiceCallbackRegistrar {

  private final QueueChainingService queueChainingService;
  private final StepService stepService;

  /**
   * Registers all callback handlers after bean construction.
   *
   * <p>This method is called automatically by Spring after dependency injection. It registers the
   * step service methods as callbacks for the ready queue, delay queue, and external update queue.
   */
  @PostConstruct
  public void registerCallbacks() {
    // This stepService is the proxied bean, so @Transactional works
    queueChainingService.setCallbackForReadyQueue(stepService::handleReadyEvent);
    queueChainingService.setCallbackForDelayQueue(stepService::handleDelayEvent);
    queueChainingService.setCallbackForExternalUpdateQueue(stepService::handleExternalUpdateEvent);
  }
}
