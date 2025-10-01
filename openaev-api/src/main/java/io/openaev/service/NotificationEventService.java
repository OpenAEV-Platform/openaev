package io.openaev.service;

import io.openaev.database.model.NotificationRuleResourceType;
import io.openaev.notification.handler.NotificationEventHandler;
import io.openaev.notification.handler.ScenarioNotificationEventHandler;
import io.openaev.notification.model.NotificationEvent;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationEventService {

  private final ApplicationEventPublisher appPublisher;
  private final ScenarioNotificationEventHandler scenarioNotificationEventHandler;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final Map<NotificationRuleResourceType, NotificationEventHandler> handlers;

  @Autowired
  public NotificationEventService(
      ApplicationEventPublisher appPublisher,
      ScenarioNotificationEventHandler scenarioNotificationEventHandler,
      ThreadPoolTaskScheduler taskScheduler) {

    this.appPublisher = appPublisher;
    this.scenarioNotificationEventHandler = scenarioNotificationEventHandler;
    this.taskScheduler = taskScheduler;
    this.handlers = Map.of(NotificationRuleResourceType.SCENARIO, scenarioNotificationEventHandler);
  }

  public void sendNotificationEvent(@NotNull final NotificationEvent notificationEvent) {
    appPublisher.publishEvent(notificationEvent);
  }

  /**
   * Send a notification with a delay (in seconds)
   *
   * @param notificationEvent notification to send
   * @param delay delay in seconds
   */
  public void sendNotificationEventWithDelay(
      @NotNull final NotificationEvent notificationEvent, long delay) {
    taskScheduler.schedule(
        () -> sendNotificationEvent(notificationEvent),
        Instant.now().plus(delay, ChronoUnit.SECONDS));
  }

  @EventListener
  public void handleNotificationEvent(@NotNull final NotificationEvent event) {
    NotificationEventHandler handler =
        Optional.ofNullable(handlers.get(event.getResourceType()))
            .orElseThrow(
                () ->
                    new UnsupportedOperationException(
                        "No handler registered for resource type " + event.getResourceType()));
    handler.handle(event);
  }
}
