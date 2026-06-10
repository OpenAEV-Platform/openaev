package io.openaev.service;

import static org.mockito.Mockito.verify;

import io.openaev.database.model.NotificationRuleResourceType;
import io.openaev.notification.handler.ScenarioNotificationEventHandler;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ExtendWith(MockitoExtension.class)
public class NotificationEvenServiceTest {

  @Mock private ApplicationEventPublisher appPublisher;
  @Mock private ScenarioNotificationEventHandler scenarioNotificationEventHandler;
  @Mock private ThreadPoolTaskScheduler taskScheduler;

  private NotificationEventService notificationEventService;

  @BeforeEach
  public void setUp() {
    notificationEventService =
        new NotificationEventService(appPublisher, scenarioNotificationEventHandler, taskScheduler);
  }

  @Test
  public void given_scenarioNotificationEvent_should_delegateToScenarioHandler() {
    // -------- Arrange --------
    NotificationEvent notificationEvent =
        NotificationEvent.builder()
            .eventType(NotificationEventType.SIMULATION_COMPLETED)
            .resourceType(NotificationRuleResourceType.SCENARIO)
            .timestamp(Instant.now())
            .resourceId("id")
            .build();

    // -------- Act --------
    notificationEventService.handleNotificationEvent(notificationEvent);

    // -------- Assert --------
    verify(scenarioNotificationEventHandler).handle(notificationEvent);
  }

  @Test
  public void given_notificationEvent_should_publishToApplicationEventBus() {
    // -------- Arrange --------
    NotificationEvent notificationEvent =
        NotificationEvent.builder()
            .eventType(NotificationEventType.SIMULATION_COMPLETED)
            .resourceType(NotificationRuleResourceType.SCENARIO)
            .timestamp(Instant.now())
            .resourceId("id")
            .build();

    // -------- Act --------
    notificationEventService.sendNotificationEvent(notificationEvent);

    // -------- Assert --------
    verify(appPublisher).publishEvent(notificationEvent);
  }
}
