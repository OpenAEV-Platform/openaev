package io.openaev.notification.catalog;

import static io.openaev.database.model.NotificationTriggerEventType.SYNC_BLOCKED;

import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseRefused;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends a sync blocked event when a catalogue release is refused, whole or in part. */
@Component
@RequiredArgsConstructor
public class CatalogNotificationListener {

  private final NotificationEngineService notificationEngineService;

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onReleaseApplied(CatalogReleaseApplied event) {
    if (event.refused().isEmpty()) {
      return;
    }
    // First line for the list, then one line per refused item for the details.
    String refused =
        event.refused().stream()
            .map(r -> "\n- " + r.name() + " v" + r.version() + ": " + r.reason())
            .collect(Collectors.joining());
    syncBlocked(
        event.tenantId(),
        event.release(),
        "[catalog] Release %s: %s refused%s"
            .formatted(event.release(), count(event.refused().size(), "item"), refused));
  }

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK, fallbackExecution = true)
  public void onReleaseRefused(CatalogReleaseRefused event) {
    String release = event.release() == null ? "without a name" : event.release();
    syncBlocked(
        event.tenantId(),
        release,
        "[catalog] Release %s refused: %s".formatted(release, event.reason()));
  }

  private void syncBlocked(String tenantId, String release, String message) {
    notificationEngineService.handleEventWithMessage(
        NotificationResourceCatalog.CATALOG_SYNC, release, tenantId, SYNC_BLOCKED, message);
  }

  private static String count(long count, String noun) {
    return count + " " + noun + (count == 1 ? "" : "s");
  }
}
