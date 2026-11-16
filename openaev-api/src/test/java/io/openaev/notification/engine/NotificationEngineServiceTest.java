package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

@DisplayName("Notification engine live stage")
class NotificationEngineServiceTest {

  private NotificationTriggerCacheService cacheService;
  private NotificationMatchingService matchingService;
  private NotificationDispatchService dispatchService;
  private NotificationEventRecordRepository eventRecordRepository;
  private TenantScopedTransaction tenantTx;
  private MarkingClearanceCacheManager markingClearanceCacheManager;
  private PreviewFeatureService previewFeatureService;
  private NotificationMarkingResolver markingResolver;
  private NotificationEngineService engineService;

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @BeforeEach
  void setUp() {
    cacheService = mock(NotificationTriggerCacheService.class);
    matchingService = mock(NotificationMatchingService.class);
    dispatchService = mock(NotificationDispatchService.class);
    eventRecordRepository = mock(NotificationEventRecordRepository.class);
    tenantTx = mock(TenantScopedTransaction.class);
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), ArgumentMatchers.<Runnable>any());
    markingClearanceCacheManager = mock(MarkingClearanceCacheManager.class);
    previewFeatureService = mock(PreviewFeatureService.class);
    markingResolver = mock(NotificationMarkingResolver.class);
    // Default: no markings relevant to the event, matching the real resolver's "nothing to
    // narrow" shape for every resource type not explicitly stubbed by a test.
    when(markingResolver.resolveMarkingSets(any(), anyString(), anyString(), any()))
        .thenReturn(List.of());
    engineService =
        new NotificationEngineService(
            cacheService,
            matchingService,
            dispatchService,
            eventRecordRepository,
            tenantTx,
            markingClearanceCacheManager,
            previewFeatureService,
            markingResolver);
  }

  private ResolvedNotificationTrigger liveTrigger(
      Set<NotificationTriggerEventType> eventTypes, String tenantId, List<String> userIds) {
    return new ResolvedNotificationTrigger(
        "trigger-id",
        "My trigger",
        NotificationTriggerType.LIVE,
        ResourceType.SCENARIO,
        eventTypes,
        null,
        null,
        null,
        null,
        List.of(),
        tenantId,
        userIds,
        List.of(
            new ResolvedNotifier(
                "notifier-id",
                "UI",
                io.openaev.database.model.NotifierType.UI,
                java.util.Map.of())));
  }

  @Test
  @DisplayName("A matching event records one outbox row per recipient and dispatches")
  void matchingEventRecordsAndDispatches() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(
            Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1", "user-2"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My scenario");

    ArgumentCaptor<List<NotificationEventRecord>> recordsCaptor = ArgumentCaptor.captor();
    verify(eventRecordRepository, times(1)).saveAll(recordsCaptor.capture());
    assertEquals(2, recordsCaptor.getValue().size());
    verify(tenantTx).execute(any(TxCtx.class), ArgumentMatchers.<Runnable>any());
    verify(dispatchService)
        .dispatch(
            eq(trigger),
            eq(NotificationTriggerType.LIVE),
            eq(List.of("user-1", "user-2")),
            anyList());
  }

  @Test
  @DisplayName("Events of another tenant never match a trigger (tenant isolation)")
  void crossTenantEventsAreSkipped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_B,
        NotificationTriggerEventType.CREATE,
        "My scenario");

    verify(matchingService, never()).matches(any(), any(), anyString());
    verify(eventRecordRepository, never()).saveAll(anyCollection());
    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("Events without a resolved tenant are dropped (fail-closed tenant isolation)")
  void nullTenantEventsAreSkipped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        null,
        NotificationTriggerEventType.CREATE,
        "My scenario");

    verify(matchingService, never()).matches(any(), any(), anyString());
    verify(eventRecordRepository, never()).saveAll(anyCollection());
    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("Events of an unsubscribed type are skipped")
  void unsubscribedEventTypesAreSkipped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.DELETE,
        "My scenario");

    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("Non-matching filters do not dispatch")
  void nonMatchingFiltersAreSkipped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(false);

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My scenario");

    verify(eventRecordRepository, never()).saveAll(anyCollection());
    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("Live dispatch is narrowed to recipients cleared for the event's marking sets")
  void dispatchIsNarrowedByMarkingClearance() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(
            Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1", "user-2"));
    when(cacheService.getLiveTriggers(ResourceType.ASSET)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(true);
    when(markingResolver.resolveMarkingSets(
            ResourceType.ASSET, "asset-id", TENANT_A, NotificationTriggerEventType.CREATE))
        .thenReturn(List.<String[]>of(new String[] {"tlp-red"}));
    when(markingClearanceCacheManager.findClearance("user-1", TENANT_A))
        .thenReturn(MarkingCtx.forMarkings(List.of("tlp-red")));
    when(markingClearanceCacheManager.findClearance("user-2", TENANT_A))
        .thenReturn(MarkingCtx.none());

    engineService.handleEvent(
        NotificationResourceCatalog.ASSET,
        "asset-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My asset");

    verify(dispatchService)
        .dispatch(eq(trigger), eq(NotificationTriggerType.LIVE), eq(List.of("user-1")), anyList());
  }

  @Test
  @DisplayName("Dispatch stays unfiltered when the event carries no marking sets")
  void dispatchIsUnfilteredWithoutMarkingSets() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(
            Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1", "user-2"));
    when(cacheService.getLiveTriggers(ResourceType.ASSET)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);

    engineService.handleEvent(
        NotificationResourceCatalog.ASSET,
        "asset-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My asset");

    verify(dispatchService)
        .dispatch(
            eq(trigger),
            eq(NotificationTriggerType.LIVE),
            eq(List.of("user-1", "user-2")),
            anyList());
    verify(markingClearanceCacheManager, never()).findClearance(anyString(), anyString());
  }

  @Test
  @DisplayName("A finding is notifiable through its least-restricted linked asset (OR across them)")
  void findingDispatchIsNarrowedByUnionOfLinkedAssetMarkings() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(
            Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1", "user-2"));
    when(cacheService.getLiveTriggers(ResourceType.FINDING)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(true);
    // Two linked assets: one TLP:RED, one unmarked - unmarked keeps the finding visible to all.
    when(markingResolver.resolveMarkingSets(
            ResourceType.FINDING, "finding-id", TENANT_A, NotificationTriggerEventType.CREATE))
        .thenReturn(List.of(new String[] {"tlp-red"}, new String[0]));
    when(markingClearanceCacheManager.findClearance(anyString(), eq(TENANT_A)))
        .thenReturn(MarkingCtx.none());

    engineService.handleEvent(
        NotificationResourceCatalog.FINDING,
        "finding-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My finding");

    verify(dispatchService)
        .dispatch(
            eq(trigger),
            eq(NotificationTriggerType.LIVE),
            eq(List.of("user-1", "user-2")),
            anyList());
  }

  @Test
  @DisplayName("An inject's recipients are narrowed by the resolved marking sets")
  void injectDispatchIsNarrowedByResolvedMarkingSets() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.INJECT)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);
    when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(true);
    when(markingResolver.resolveMarkingSets(
            ResourceType.INJECT, "inject-id", TENANT_A, NotificationTriggerEventType.CREATE))
        .thenReturn(List.<String[]>of(new String[] {"tlp-red"}));
    when(markingClearanceCacheManager.findClearance("user-1", TENANT_A))
        .thenReturn(MarkingCtx.none());

    engineService.handleEvent(
        NotificationResourceCatalog.INJECT,
        "inject-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My inject");

    verify(dispatchService)
        .dispatch(eq(trigger), eq(NotificationTriggerType.LIVE), eq(List.of()), anyList());
  }
}
