package io.openaev.service.chaining;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class QueueChainingServiceCallbackRegistrar {

  private final QueueChainingService queueChainingService;
  private final StepService stepService;

  @PostConstruct
  public void registerCallbacks() {
    // This stepService is the proxied bean, so @Transactional works
    queueChainingService.setCallbackForWaitQueue(stepService::handleWaitEvent);
    queueChainingService.setCallbackForDelayQueue(stepService::handleDelayEvent);
    queueChainingService.setCallbackForExternalUpdateQueue(stepService::handleUpdateEvent);
  }
}
