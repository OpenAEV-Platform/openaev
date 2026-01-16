package io.openaev.service.chaining;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class QueueChainingServiceCallbackRegistrar {

  private final QueueChainingService queueChainingService;
  private final StepService stepService;

  public QueueChainingServiceCallbackRegistrar(QueueChainingService queueChainingService, StepService stepService) {
    this.queueChainingService = queueChainingService;
    this.stepService = stepService;
  }

  @PostConstruct
  public void registerCallbacks() {
    // This stepService is the proxied bean, so @Transactional works
    queueChainingService.setCallbackForWaitQueue(stepService::handleWaitEvent);
    queueChainingService.setCallbackForDelayQueue(stepService::handleDelayEvent);
    queueChainingService.setCallbackForExternalUpdateQueue(stepService::handleUpdateEvent);
  }
}