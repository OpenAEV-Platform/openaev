package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

import io.openaev.IntegrationTest;
import io.openaev.database.model.NotificationRuleResourceType;
import io.openaev.notification.handler.ScenarioNotificationEventHandler;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.utilstest.RabbitMQTestListener;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class NotificationEvenServiceTest extends IntegrationTest {

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
  public void test_handleEvent() {
    NotificationEvent notificationEvent =
        NotificationEvent.builder()
            .eventType(NotificationEventType.SIMULATION_COMPLETED)
            .resourceType(NotificationRuleResourceType.SCENARIO)
            .timestamp(Instant.now())
            .resourceId("id")
            .build();
    notificationEventService.handleNotificationEvent(notificationEvent);
    verify(scenarioNotificationEventHandler).handle(notificationEvent);
  }

  @Test
  public void test_send_event() {
    NotificationEvent notificationEvent =
        NotificationEvent.builder()
            .eventType(NotificationEventType.SIMULATION_COMPLETED)
            .resourceType(NotificationRuleResourceType.SCENARIO)
            .timestamp(Instant.now())
            .resourceId("id")
            .build();
    notificationEventService.sendNotificationEvent(notificationEvent);
    verify(appPublisher).publishEvent(notificationEvent);
  }
}
