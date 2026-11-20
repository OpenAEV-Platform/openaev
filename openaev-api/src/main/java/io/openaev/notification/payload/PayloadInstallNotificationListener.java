package io.openaev.notification.payload;

import static io.openaev.database.model.NotificationTriggerEventType.INSTALLED_AUTOMATICALLY;
import static io.openaev.database.model.NotificationTriggerEventType.NEW_PAYLOAD_VERSION;
import static io.openaev.notification.content.ContentNotification.count;
import static io.openaev.notification.content.ContentNotification.named;

import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.notification.content.ContentNotification;
import io.openaev.notification.content.ContentNotifier;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.catalog.event.CatalogContentChanged;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the tenant a catalogue payload version was installed by itself, and which scenarios still
 * run an older version once it is the latest.
 */
@Component
@RequiredArgsConstructor
public class PayloadInstallNotificationListener {

  private final ContentNotifier contentNotifier;
  private final PayloadService payloadService;
  private final PayloadVersionService payloadVersionService;

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onContentChanged(CatalogContentChanged event) {
    if (event.type() != ContentType.PAYLOAD || event.action() != CatalogHistory.Action.INSTALL) {
      return;
    }
    contentNotifier.send(event.tenantId(), () -> notifications(event));
  }

  private List<ContentNotification> notifications(CatalogContentChanged event) {
    List<Payload> versions =
        payloadService.versions(event.tenantId(), event.externalId()).stream()
            .filter(version -> event.repository().equals(version.getCatalogRepository()))
            .toList();
    Payload installed =
        versions.stream()
            .filter(version -> event.version().equals(version.getVersion()))
            .findFirst()
            .orElseThrow();
    List<ContentNotification> notifications = new ArrayList<>();
    if (event.mode() == CatalogHistory.Mode.AUTO) {
      notifications.add(
          ContentNotification.payload(
              installed, INSTALLED_AUTOMATICALLY, " installed automatically"));
    }
    List<Payload> older =
        versions.stream()
            .filter(
                version ->
                    SemanticVersion.ORDER.compare(version.getVersion(), installed.getVersion()) < 0)
            .toList();
    if (installed.isLatest() && !older.isEmpty()) {
      notifications.addAll(
          ContentNotification.scenarios(
              payloadVersionService.scenarioUsages(event.tenantId(), older),
              NEW_PAYLOAD_VERSION,
              injects ->
                  "%s is available, %s still %s an older version"
                      .formatted(
                          named(installed),
                          count(injects, "inject"),
                          injects == 1 ? "runs" : "run")));
    }
    return notifications;
  }
}
