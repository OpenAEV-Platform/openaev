package io.openaev.notification.engine;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTriggerType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Digest stage of the notifications engine: evaluated every minute by the digest job. Digests due
 * at the current minute replay the {@link NotificationEventRecord} outbox over their period window
 * (the OpenAEV equivalent of OpenCTI re-reading {@code stream.notification}) and deliver one
 * aggregated notification per recipient - each outbox record re-checked against the recipient's
 * marking clearance at send time via {@link NotificationMarkingResolver}, since the outbox itself
 * carries no marking snapshot.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationDigestService {

  private final NotificationTriggerLoader triggerLoader;
  private final NotificationDispatchService dispatchService;
  private final TenantScopedTransaction tenantTx;
  private final NotificationMarkingResolver markingResolver;

  public void runDigests(Instant now) {
    List<ResolvedNotificationTrigger> digests =
        triggerLoader.loadEnabledTriggers(NotificationTriggerType.DIGEST);
    for (ResolvedNotificationTrigger digest : digests) {
      try {
        if (!NotificationTriggerTimeUtils.isTimeTrigger(
            digest.period(), digest.triggerTime(), now)) {
          continue;
        }
        processDigest(digest, now);
      } catch (Exception e) {
        log.error("Digest trigger {} processing failed", digest.id(), e);
      }
    }
  }

  private void processDigest(ResolvedNotificationTrigger digest, Instant now) {
    if (digest.childTriggerIds().isEmpty()) {
      return;
    }
    Instant to = now.truncatedTo(ChronoUnit.MINUTES);
    Instant from = NotificationTriggerTimeUtils.windowStart(digest.period(), to);
    List<NotificationEventRecord> events =
        triggerLoader.loadEventsWindow(digest.childTriggerIds(), from, to);
    if (events.isEmpty()) {
      return;
    }
    // Run with both scopes (mirrors NotificationEngineService): TenantContext arms the v1
    // filter that marking resolution (FINDING/INJECT -> their linked assets, ASSET's own
    // column) and dispatch's injector lookup both read through; TxCtx scopes any v2-active
    // table the same reads reach.
    TenantContext.setCurrentTenant(digest.tenantId());
    try {
      tenantTx.execute(
          TxCtx.forTenant(digest.tenantId()),
          () -> {
            // One aggregated notification per digest recipient, containing only the events
            // produced for that user by the composed live triggers that they are still cleared
            // to see, grouped per live trigger.
            for (String userId : digest.recipientUserIds()) {
              List<NotificationEventRecord> userEvents =
                  events.stream()
                      .filter(event -> userId.equals(event.getUser().getId()))
                      .filter(
                          event ->
                              markingResolver.isVisibleTo(
                                  userId,
                                  digest.tenantId(),
                                  event.getResourceTypeValue(),
                                  event.getResourceId(),
                                  event.getEventType()))
                      .toList();
              if (userEvents.isEmpty()) {
                continue;
              }
              Map<String, List<NotificationContent.Event>> byTriggerName = new LinkedHashMap<>();
              for (NotificationEventRecord event : userEvents) {
                byTriggerName
                    .computeIfAbsent(event.getTrigger().getName(), key -> new ArrayList<>())
                    .add(
                        new NotificationContent.Event(
                            event.getEventType(),
                            event.getMessage(),
                            event.getResourceTypeValue(),
                            event.getResourceId()));
              }
              List<NotificationContent.Group> groups =
                  byTriggerName.entrySet().stream()
                      .map(entry -> new NotificationContent.Group(entry.getKey(), entry.getValue()))
                      .toList();
              dispatchService.dispatch(
                  digest, NotificationTriggerType.DIGEST, List.of(userId), groups);
            }
          });
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
