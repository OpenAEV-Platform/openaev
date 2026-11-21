package io.openaev.notification.payload;

import static io.openaev.database.model.NotificationTriggerEventType.NEW_PAYLOAD_VERSION;
import static io.openaev.notification.content.ContentNotification.count;
import static io.openaev.notification.content.ContentNotification.named;

import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.notification.catalog.CatalogEvents.InstalledAutomatically;
import io.openaev.notification.content.ContentNotification;
import io.openaev.notification.content.ContentNotifier;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.catalog.event.CatalogContentChanged;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the scenarios still running an older version of a payload once a newer one is the latest.
 */
@Component
@RequiredArgsConstructor
public class PayloadInstallNotificationListener {

  private final ContentNotifier contentNotifier;
  private final PayloadService payloadService;
  private final PayloadVersionService payloadVersionService;

  // A version installed by itself is told with the rest of its release.
  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onContentChanged(CatalogContentChanged event) {
    if (event.type() != ContentType.PAYLOAD
        || event.action() != CatalogHistory.Action.INSTALL
        || event.mode() == CatalogHistory.Mode.AUTO) {
      return;
    }
    contentNotifier.send(event.tenantId(), () -> newVersion(event.tenantId(), installed(event)));
  }

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onInstalledAutomatically(InstalledAutomatically event) {
    if (event.payloadIds().isEmpty()) {
      return;
    }
    String tenantId = event.tenantId();
    contentNotifier.send(
        tenantId,
        () ->
            event.payloadIds().stream()
                .flatMap(id -> newVersion(tenantId, payloadService.payload(tenantId, id)).stream())
                .toList());
  }

  private Payload installed(CatalogContentChanged event) {
    return payloadService.versions(event.tenantId(), event.externalId()).stream()
        .filter(version -> event.repository().equals(version.getCatalogRepository()))
        .filter(version -> event.version().equals(version.getVersion()))
        .findFirst()
        .orElseThrow();
  }

  private List<ContentNotification> newVersion(String tenantId, Payload installed) {
    List<Payload> older =
        payloadService.versionsOf(tenantId, installed).stream()
            .filter(
                version ->
                    SemanticVersion.ORDER.compare(version.getVersion(), installed.getVersion()) < 0)
            .toList();
    if (!installed.isLatest() || older.isEmpty()) {
      return List.of();
    }
    return ContentNotification.scenarios(
        payloadVersionService.scenarioUsages(tenantId, older),
        NEW_PAYLOAD_VERSION,
        injects ->
            "%s is available, %s still %s an older version"
                .formatted(
                    named(installed), count(injects, "inject"), injects == 1 ? "runs" : "run"));
  }
}
