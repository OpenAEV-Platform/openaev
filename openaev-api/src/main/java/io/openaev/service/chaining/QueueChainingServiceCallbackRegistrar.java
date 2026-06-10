package io.openaev.service.chaining;

import io.openaev.context.TxCtx;
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
  private final StepEventService stepEventService;

  /**
   * Registers all callback handlers after bean construction.
   *
   * <p>This method is called automatically by Spring after dependency injection. It registers the
   * step event service methods as callbacks for the ready queue, delay queue, and external update
   * queue.
   */
  @PostConstruct
  public void registerCallbacks() {
    TxCtx ctx = TxCtx.noTenant();
    // This stepEventService is the proxied bean, so @Transactional works
    queueChainingService.setCallbackForReadyQueue(
        events -> {
          events.forEach(event -> stepEventService.handleReadyStepEvent(ctx, event));
          return events;
        });
    queueChainingService.setCallbackForExternalUpdateQueue(
        events -> {
          events.forEach(event -> stepEventService.handleExternalUpdateEvent(ctx, event));
          return events;
        });
  }
}
