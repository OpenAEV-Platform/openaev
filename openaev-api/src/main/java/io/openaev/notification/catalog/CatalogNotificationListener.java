package io.openaev.notification.catalog;

import static io.openaev.database.model.NotificationTriggerEventType.RELEASE_RECEIVED;
import static io.openaev.database.model.NotificationTriggerEventType.SYNC_BLOCKED;
import static io.openaev.notification.content.ContentNotification.count;

import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.notification.catalog.CatalogEvents.AddedVersion;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseApplied;
import io.openaev.notification.catalog.CatalogEvents.CatalogReleaseRefused;
import io.openaev.notification.catalog.CatalogEvents.ScenariosInstalledAutomatically;
import io.openaev.notification.content.ContentNotification;
import io.openaev.notification.content.ContentNotifier;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.service.catalog.CatalogOffer;
import io.openaev.service.catalog.scenario.CatalogScenarioService;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Tells the tenant what a catalogue release brings, and when it is refused, whole or in part. */
@Component
@RequiredArgsConstructor
public class CatalogNotificationListener {

  private final ContentNotifier contentNotifier;
  private final CatalogScenarioService catalogScenarioService;

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onReleaseApplied(CatalogReleaseApplied event) {
    contentNotifier.send(
        event.tenantId(),
        () -> {
          List<ContentNotification> notifications = new ArrayList<>();
          List<String> news = news(event);
          if (!news.isEmpty()) {
            notifications.add(
                release(event.release(), RELEASE_RECEIVED, ": " + String.join("; ", news)));
          }
          if (!event.refused().isEmpty()) {
            notifications.add(syncBlocked(event));
          }
          return notifications;
        });
  }

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK, fallbackExecution = true)
  public void onReleaseRefused(CatalogReleaseRefused event) {
    String release = event.release() == null ? "without a name" : event.release();
    contentNotifier.send(
        event.tenantId(),
        () -> List.of(release(release, SYNC_BLOCKED, " refused: " + event.reason())));
  }

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onScenariosInstalledAutomatically(ScenariosInstalledAutomatically event) {
    List<String> parts = new ArrayList<>();
    listed(parts, event.installed(), "scenario", " installed automatically");
    listed(parts, event.failed(), "scenario", " failed to install");
    contentNotifier.send(
        event.tenantId(),
        () -> List.of(release(event.release(), RELEASE_RECEIVED, ": " + String.join("; ", parts))));
  }

  // -- RELEASE RECEIVED --

  // The first release fills the whole catalogue: nothing in it is news.
  private List<String> news(CatalogReleaseApplied event) {
    List<String> news = new ArrayList<>();
    if (event.firstSync()) {
      return news;
    }
    Set<String> installed = catalogScenarioService.installedKeys(event.tenantId());
    List<String> newScenarios = new ArrayList<>();
    List<String> updates = new ArrayList<>();
    int newActions = 0;
    for (List<AddedVersion> versions : byContent(event.added())) {
      AddedVersion latest =
          versions.stream()
              .max(Comparator.comparing(AddedVersion::version, SemanticVersion.ORDER))
              .orElseThrow();
      boolean isNew = versions.stream().anyMatch(AddedVersion::isNew);
      boolean scenario = latest.type() == ContentType.SCENARIO;
      if (scenario
          && installed.contains(CatalogOffer.key(latest.repository(), latest.externalId()))) {
        updates.add(latest.name() + " v" + latest.version());
      } else if (scenario && isNew) {
        newScenarios.add(latest.name());
      } else if (isNew) {
        newActions++;
      }
    }
    listed(news, newScenarios, "new scenario", "");
    listed(news, updates, "installed scenario", " with an update available");
    if (newActions > 0) {
      news.add(count(newActions, "new action"));
    }
    return news;
  }

  private static Collection<List<AddedVersion>> byContent(List<AddedVersion> added) {
    return added.stream()
        .collect(
            Collectors.groupingBy(
                version ->
                    version.type()
                        + ":"
                        + CatalogOffer.key(version.repository(), version.externalId()),
                LinkedHashMap::new,
                Collectors.toList()))
        .values();
  }

  // -- SYNC BLOCKED --

  // First line for the list, then one line per refused item for the details.
  private static ContentNotification syncBlocked(CatalogReleaseApplied event) {
    String refused =
        event.refused().stream()
            .map(r -> "\n- " + r.name() + " v" + r.version() + ": " + r.reason())
            .collect(Collectors.joining());
    return release(
        event.release(),
        SYNC_BLOCKED,
        ": " + count(event.refused().size(), "item") + " refused" + refused);
  }

  // -- MESSAGE --

  private static void listed(List<String> parts, List<String> names, String noun, String what) {
    if (!names.isEmpty()) {
      parts.add(count(names.size(), noun) + what + " (" + String.join(", ", names) + ")");
    }
  }

  private static ContentNotification release(
      String release, NotificationTriggerEventType eventType, String what) {
    return new ContentNotification(
        NotificationResourceCatalog.CATALOG_SYNC,
        release,
        eventType,
        "[catalog] Release " + release + what);
  }
}
