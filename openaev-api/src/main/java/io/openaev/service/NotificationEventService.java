package io.openaev.service;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ResourceType;
import io.openaev.notification.handler.NotificationEventHandler;
import io.openaev.notification.handler.ScenarioNotificationEventHandler;
import io.openaev.notification.handler.SimulationNotificationEventHandler;
import io.openaev.notification.model.NotificationEvent;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationEventService {

  private final ApplicationEventPublisher appPublisher;
  private final ScenarioNotificationEventHandler scenarioNotificationEventHandler;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final Map<ResourceType, NotificationEventHandler> handlers;

  @Autowired
  public NotificationEventService(
      ApplicationEventPublisher appPublisher,
      // Lazy: both handlers read exercises through ExerciseService, which reaches
      // WorkflowEndService, which sends the completed event through this service; a lazy proxy
      // breaks that bean cycle (the handlers only run when an event is handled).
      @Lazy ScenarioNotificationEventHandler scenarioNotificationEventHandler,
      @Lazy SimulationNotificationEventHandler simulationNotificationEventHandler,
      ThreadPoolTaskScheduler taskScheduler) {

    this.appPublisher = appPublisher;
    this.scenarioNotificationEventHandler = scenarioNotificationEventHandler;
    this.taskScheduler = taskScheduler;
    this.handlers =
        Map.of(
            ResourceType.SCENARIO,
            scenarioNotificationEventHandler,
            ResourceType.SIMULATION,
            simulationNotificationEventHandler);
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

  /**
   * Sends {@code SIMULATION_COMPLETED} on the simulation itself, a few seconds later so the
   * transaction that finished it has committed. Its handler reads nothing unless a trigger of the
   * tenant listens to an event it fires.
   *
   * @param simulation the finished simulation
   */
  public void sendSimulationCompleted(@NotNull final Exercise simulation) {
    sendNotificationEventWithDelay(
        SimulationNotificationEventHandler.completedEvent(simulation),
        SimulationNotificationEventHandler.COMMIT_DELAY_SECONDS);
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
