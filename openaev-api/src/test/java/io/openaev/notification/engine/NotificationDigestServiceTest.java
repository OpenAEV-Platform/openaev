package io.openaev.notification.engine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerPeriod;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.NotifierType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Notification engine digest stage")
class NotificationDigestServiceTest {

  private NotificationTriggerLoader triggerLoader;
  private NotificationDispatchService dispatchService;
  private NotificationDigestService digestService;

  // 2026-07-20 09:00 UTC is a Monday
  private static final Instant DUE = Instant.parse("2026-07-20T09:00:00Z");

  @BeforeEach
  void setUp() {
    triggerLoader = mock(NotificationTriggerLoader.class);
    dispatchService = mock(NotificationDispatchService.class);
    TenantScopedTransaction tenantTx = mock(TenantScopedTransaction.class);
    // Stand in for the real primitive: just run the work on this thread.
    doAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), any(Runnable.class));
    digestService = new NotificationDigestService(triggerLoader, dispatchService, tenantTx);
  }

  private ResolvedNotificationTrigger digest(List<String> childIds, List<String> userIds) {
    return new ResolvedNotificationTrigger(
        "digest-id",
        "My digest",
        NotificationTriggerType.DIGEST,
        null,
        Set.of(),
        null,
        null,
        NotificationTriggerPeriod.DAY,
        "09:00",
        childIds,
        "tenant-a",
        userIds,
        List.of(new ResolvedNotifier("notifier-id", "UI", NotifierType.UI, Map.of())));
  }

  private NotificationEventRecord event(String triggerName, String userId) {
    NotificationEventRecord record = new NotificationEventRecord();
    NotificationTrigger trigger = new NotificationTrigger();
    trigger.setId("child-1");
    trigger.setName(triggerName);
    record.setTrigger(trigger);
    User user = new User();
    user.setId(userId);
    record.setUser(user);
    record.setEventType(NotificationTriggerEventType.CREATE);
    record.setMessage("[scenario] Test created");
    record.setResourceTypeValue(ResourceType.SCENARIO);
    record.setResourceId("scenario-id");
    return record;
  }

  @Test
  @DisplayName("A due digest aggregates the window per recipient and dispatches once per user")
  void dueDigestDispatchesPerUser() {
    ResolvedNotificationTrigger digest = digest(List.of("child-1"), List.of("user-1", "user-2"));
    when(triggerLoader.loadEnabledTriggers(NotificationTriggerType.DIGEST))
        .thenReturn(List.of(digest));
    when(triggerLoader.loadEventsWindow(eq(List.of("child-1")), any(), any()))
        .thenReturn(List.of(event("Live trigger", "user-1"), event("Live trigger", "user-1")));

    digestService.runDigests(DUE);

    // user-1 has 2 events grouped under the live trigger name; user-2 has none
    verify(dispatchService)
        .dispatch(
            eq(digest),
            eq(NotificationTriggerType.DIGEST),
            eq(List.of("user-1")),
            argThat(
                (List<NotificationContent.Group> groups) ->
                    groups.size() == 1
                        && "Live trigger".equals(groups.getFirst().title())
                        && groups.getFirst().events().size() == 2));
    verify(dispatchService, never()).dispatch(any(), any(), eq(List.of("user-2")), anyList());
  }

  @Test
  @DisplayName("A digest that is not due does nothing")
  void notDueDigestIsSkipped() {
    ResolvedNotificationTrigger digest = digest(List.of("child-1"), List.of("user-1"));
    when(triggerLoader.loadEnabledTriggers(NotificationTriggerType.DIGEST))
        .thenReturn(List.of(digest));

    digestService.runDigests(Instant.parse("2026-07-20T10:30:00Z"));

    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("A due digest without composed triggers does nothing")
  void digestWithoutChildrenIsSkipped() {
    ResolvedNotificationTrigger digest = digest(List.of(), List.of("user-1"));
    when(triggerLoader.loadEnabledTriggers(NotificationTriggerType.DIGEST))
        .thenReturn(List.of(digest));

    digestService.runDigests(DUE);

    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("A due digest with an empty window does not dispatch")
  void emptyWindowDoesNotDispatch() {
    ResolvedNotificationTrigger digest = digest(List.of("child-1"), List.of("user-1"));
    when(triggerLoader.loadEnabledTriggers(NotificationTriggerType.DIGEST))
        .thenReturn(List.of(digest));
    when(triggerLoader.loadEventsWindow(eq(List.of("child-1")), any(), any()))
        .thenReturn(List.of());

    digestService.runDigests(DUE);

    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }
}
