package io.openaev.notification.engine;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Digest stage of the notifications engine: evaluated every minute by the digest job. Digests due
 * at the current minute replay the {@link NotificationEventRecord} outbox over their period window
 * (the OpenAEV equivalent of OpenCTI re-reading {@code stream.notification}) and deliver one
 * aggregated notification per recipient.
 *
 * <p>Each row is re-checked at send time against the recipient's current rights ({@link
 * NotificationRecipientFilter}, in the digest's tenant): a row whose object the recipient no longer
 * reads is dropped, except a delete-type row (a deletion or a loss of visibility), which is kept
 * with the object's name masked as "Restricted".
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationDigestService {

  private final NotificationTriggerLoader triggerLoader;
  private final NotificationDispatchService dispatchService;
  private final NotificationRecipientFilter recipientFilter;
  private final TenantScopedTransaction tenantTx;

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
    // One aggregated notification per digest recipient, containing only the events produced for
    // that user by the composed live triggers, grouped per live trigger.
    for (String userId : digest.recipientUserIds()) {
      List<NotificationEventRecord> userEvents =
          events.stream().filter(event -> userId.equals(event.getUser().getId())).toList();
      if (userEvents.isEmpty()) {
        continue;
      }
      // Background job (no ambient transaction, no v2 scope): the re-check and the dispatch read
      // tenant-scoped tables (grants, injectors for the email notifier) so they run under an
      // explicit per-tenant scope, never a raw @Transactional here.
      tenantTx.execute(
          TxCtx.forTenant(digest.tenantId()),
          () -> {
            List<NotificationContent.Group> groups =
                groupsFor(digest.tenantId(), userId, userEvents);
            if (groups.isEmpty()) {
              return;
            }
            dispatchService.dispatch(
                digest, NotificationTriggerType.DIGEST, List.of(userId), groups);
          });
    }
  }

  private List<NotificationContent.Group> groupsFor(
      String tenantId, String userId, List<NotificationEventRecord> userEvents) {
    Map<String, List<NotificationContent.Event>> byTriggerName = new LinkedHashMap<>();
    for (NotificationEventRecord event : userEvents) {
      Optional<String> message = messageAtSendTime(tenantId, userId, event);
      if (message.isEmpty()) {
        continue;
      }
      byTriggerName
          .computeIfAbsent(event.getTrigger().getName(), key -> new ArrayList<>())
          .add(
              new NotificationContent.Event(
                  event.getEventType(),
                  message.get(),
                  event.getResourceTypeValue(),
                  event.getResourceId()));
    }
    return byTriggerName.entrySet().stream()
        .map(entry -> new NotificationContent.Group(entry.getKey(), entry.getValue()))
        .toList();
  }

  /**
   * The row's message if the recipient still reads its object; for a delete-type row it no longer
   * reads (the object is gone or was taken away), the message with the name masked; else nothing.
   * Fails closed: a row whose type is not in the catalogue is dropped.
   */
  private Optional<String> messageAtSendTime(
      String tenantId, String userId, NotificationEventRecord event) {
    Optional<NotificationResourceCatalog> entry =
        NotificationResourceCatalog.fromResourceType(event.getResourceTypeValue());
    if (entry.isEmpty()) {
      return Optional.empty();
    }
    if (recipientFilter.canRead(tenantId, userId, entry.get(), event.getResourceId())) {
      return Optional.ofNullable(event.getMessage());
    }
    if (event.getEventType() != NotificationTriggerEventType.DELETE) {
      return Optional.empty();
    }
    String message = event.getMessage();
    if (message != null && message.endsWith(NotificationEngineService.NO_LONGER_VISIBLE)) {
      return Optional.of(message);
    }
    return Optional.of(
        NotificationEngineService.maskedDeletedMessage(event.getResourceTypeValue()));
  }
}
