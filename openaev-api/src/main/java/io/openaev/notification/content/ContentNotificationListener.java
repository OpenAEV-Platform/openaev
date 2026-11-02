package io.openaev.notification.content;

import static io.openaev.database.model.NotificationTriggerEventType.*;

import io.openaev.api.threat_arsenal.PayloadUsageOutput;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CatalogItem;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadIdentity;
import io.openaev.database.model.ResourceType;
import io.openaev.notification.content.ContentNotificationEvents.*;
import io.openaev.notification.engine.NotificationContent;
import io.openaev.notification.engine.NotificationDispatchService;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.service.UserService;
import io.openaev.service.catalog.CatalogService;
import io.openaev.service.payload.PayloadVersionChangeService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.SemanticVersion;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns content changes (revocations, new versions, catalogue releases) into notification events,
 * once their transaction commits. Messages are computed in a tenant transaction, then handed to the
 * engine, which opens its own.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ContentNotificationListener {

  private final NotificationEngineService notificationEngineService;
  private final NotificationDispatchService notificationDispatchService;
  private final TenantScopedTransaction tenantTx;
  private final TenantService tenantService;
  private final UserService userService;
  private final PayloadVersionService payloadVersionService;
  private final PayloadVersionChangeService payloadVersionChangeService;
  private final CatalogService catalogService;
  private final ScenarioService scenarioService;

  private record Emit(
      NotificationResourceCatalog entry,
      String entityId,
      NotificationTriggerEventType type,
      String message) {}

  // -- REVOCATIONS --

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onRevocationChanged(PayloadRevocationChanged event) {
    emit(
        event.tenantId(),
        () ->
            revocationEmits(
                payloadVersionService.findById(event.payloadId()),
                event.revoked(),
                event.reason(),
                false));
  }

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onListRevocationsChanged(ListRevocationsChanged event) {
    for (String tenantId : tenantService.findActiveTenantIds()) {
      emit(
          tenantId,
          () -> {
            List<Emit> emits = new ArrayList<>();
            listed(event.repository(), event.revoked())
                .forEach(
                    (payload, reason) ->
                        emits.addAll(revocationEmits(payload, true, reason, true)));
            listed(event.repository(), event.lifted())
                .forEach(
                    (payload, reason) -> emits.addAll(revocationEmits(payload, false, null, true)));
            return emits;
          });
    }
  }

  // Installed versions named by a revocation list, with the list's reason.
  private Map<Payload, String> listed(String repository, List<ListedVersion> versions) {
    Map<Payload, String> payloads = new LinkedHashMap<>();
    for (ListedVersion listed : versions) {
      payloadVersionService
          .findManagedIdentity(repository, listed.externalId())
          .flatMap(
              identity ->
                  payloadVersionService.findVersions(identity).stream()
                      .filter(p -> listed.version().equals(p.getVersion()))
                      .findFirst())
          .ifPresent(payload -> payloads.put(payload, Objects.toString(listed.reason(), "")));
    }
    return payloads;
  }

  private List<Emit> revocationEmits(
      Payload payload, boolean revoked, String reason, boolean byFiligran) {
    String action = payloadLabel(payload);
    String why = reason == null || reason.isBlank() ? "" : ": " + reason;
    List<Emit> emits = new ArrayList<>();
    if (!revoked) {
      emits.add(
          new Emit(
              NotificationResourceCatalog.PAYLOAD,
              payload.getId(),
              REVOCATION_LIFTED,
              "[payload] " + action + ": revocation lifted" + why));
      return emits;
    }
    emits.add(
        new Emit(
            NotificationResourceCatalog.PAYLOAD,
            payload.getId(),
            REVOKED,
            "[payload] " + action + " revoked" + (byFiligran ? " by Filigran" : "") + why));
    // Customized copies based on this version are revoked with it.
    List<Payload> affected = new ArrayList<>(List.of(payload));
    if (payload.isManaged()) {
      Set<String> revokedIds = new HashSet<>();
      List<Payload> copies =
          payloadVersionService.findVariantsOf(payload.getIdentity()).stream()
              .flatMap(variant -> payloadVersionService.findVersions(variant).stream())
              .filter(copy -> payload.getId().equals(copy.getOriginId()))
              .toList();
      revokedIds.addAll(payloadVersionService.revokedIds(copies));
      copies.stream().filter(copy -> revokedIds.contains(copy.getId())).forEach(affected::add);
    }
    byScenario(affected)
        .forEach(
            (scenarioId, usages) ->
                emits.add(
                    new Emit(
                        NotificationResourceCatalog.SCENARIO,
                        scenarioId,
                        PAYLOAD_REVOKED,
                        "[scenario] %s: %s revoked%s, %s can no longer run"
                            .formatted(
                                usages.getFirst().scenarioName(),
                                action,
                                reason == null || reason.isBlank() ? "" : " (" + reason + ")",
                                injects(usages.size())))));
    return emits;
  }

  // -- VERSIONS --

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onVersionAdded(PayloadVersionAdded event) {
    emit(
        event.tenantId(),
        () -> {
          Payload added = payloadVersionService.findById(event.payloadId());
          PayloadIdentity identity = added.getIdentity();
          // Payload approval: a version is available once approved (the approval sends the event
          // again); one waiting for approval is not announced.
          if (!added.getId().equals(identity.getLatestPayloadId())
              || added.getApprovalStatus() != Payload.PAYLOAD_APPROVAL_STATUS.APPROVED) {
            return List.of();
          }
          List<Emit> emits = new ArrayList<>();
          List<Payload> older =
              payloadVersionService.findVersions(identity).stream()
                  .filter(
                      v -> SemanticVersion.ORDER.compare(v.getVersion(), added.getVersion()) < 0)
                  .toList();
          byScenario(older)
              .forEach(
                  (scenarioId, usages) ->
                      emits.add(
                          new Emit(
                              NotificationResourceCatalog.SCENARIO,
                              scenarioId,
                              NEW_PAYLOAD_VERSION,
                              "[scenario] %s: %s is available, %s still %s an older version"
                                  .formatted(
                                      usages.getFirst().scenarioName(),
                                      payloadLabel(added),
                                      injects(usages.size()),
                                      usages.size() == 1 ? "runs" : "run"))));
          if (identity.isManaged()) {
            Set<String> olderIds = older.stream().map(Payload::getId).collect(Collectors.toSet());
            for (PayloadIdentity variant : payloadVersionService.findVariantsOf(identity)) {
              if (variant.getLatestPayloadId() == null) {
                continue;
              }
              Payload copy = payloadVersionService.findById(variant.getLatestPayloadId());
              if (olderIds.contains(copy.getOriginId())) {
                emits.add(
                    new Emit(
                        NotificationResourceCatalog.PAYLOAD,
                        copy.getId(),
                        BASE_UPDATE_AVAILABLE,
                        "[payload] %s: Filigran v%s is available"
                            .formatted(copy.getName(), added.getVersion())));
              }
            }
          }
          return emits;
        });
  }

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onInstalledAutomatically(PayloadInstalledAutomatically event) {
    emit(
        event.tenantId(),
        () -> {
          Payload payload = payloadVersionService.findById(event.payloadId());
          return List.of(
              new Emit(
                  NotificationResourceCatalog.PAYLOAD,
                  payload.getId(),
                  INSTALLED_AUTOMATICALLY,
                  "[payload] %s installed automatically from release %s"
                      .formatted(payloadLabel(payload), event.release())));
        });
  }

  // -- RELEASES --

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onReleaseApplied(CatalogReleaseApplied event) {
    // The first sync fills the whole catalogue: nothing in it is news.
    if (!event.firstSync()) {
      emit(
          event.tenantId(),
          () ->
              List.of(
                  new Emit(
                      NotificationResourceCatalog.CATALOG,
                      event.release(),
                      RELEASE_RECEIVED,
                      "[catalog] Release " + event.release() + ": " + releaseSummary(event))));
    }
    if (!event.refused().isEmpty()) {
      alertAdministrators(event);
    }
  }

  private String releaseSummary(CatalogReleaseApplied event) {
    List<CatalogItem> added = catalogService.findAllById(event.addedItemIds());
    Map<String, Map<String, String>> installedScenarios =
        scenarioService.findInstalledCatalogVersions();
    List<String> newScenarios = new ArrayList<>();
    List<String> scenarioUpdates = new ArrayList<>();
    int newActions = 0;
    for (CatalogItem item : latestPerExternalId(added)) {
      boolean firstVersion =
          catalogService
                  .findVersions(item.getType(), item.getRepository(), item.getExternalId())
                  .size()
              == added.stream()
                  .filter(other -> other.getExternalId().equals(item.getExternalId()))
                  .count();
      if (item.getType() == ContentType.SCENARIO) {
        if (installedScenarios.containsKey(item.getExternalId())) {
          scenarioUpdates.add(item.getName() + " v" + item.getVersion());
        } else if (firstVersion) {
          newScenarios.add(item.getName());
        }
      } else if (item.getType() == ContentType.PAYLOAD && firstVersion) {
        newActions++;
      }
    }
    List<String> parts = new ArrayList<>();
    if (!newScenarios.isEmpty()) {
      parts.add(
          count(newScenarios.size(), "new scenario")
              + " ("
              + String.join(", ", newScenarios)
              + ")");
    }
    if (!scenarioUpdates.isEmpty()) {
      parts.add(
          "update available for "
              + count(scenarioUpdates.size(), "installed scenario")
              + " ("
              + String.join(", ", scenarioUpdates)
              + ")");
    }
    if (newActions > 0) {
      parts.add(count(newActions, "new action"));
    }
    return parts.isEmpty() ? "nothing new for this tenant" : String.join("; ", parts);
  }

  private static List<CatalogItem> latestPerExternalId(List<CatalogItem> items) {
    return items.stream()
        .collect(
            Collectors.groupingBy(
                CatalogItem::getExternalId, LinkedHashMap::new, Collectors.toList()))
        .values()
        .stream()
        .map(
            versions ->
                versions.stream()
                    .max(Comparator.comparing(CatalogItem::getVersion, SemanticVersion.ORDER))
                    .orElseThrow())
        .toList();
  }

  private void alertAdministrators(CatalogReleaseApplied event) {
    String refused =
        event.refused().stream()
            .map(r -> r.externalId() + " v" + r.version() + " (" + r.reason() + ")")
            .collect(Collectors.joining(", "));
    NotificationContent.Group group =
        new NotificationContent.Group(
            "Catalog sync blocked",
            List.of(
                new NotificationContent.Event(
                    SYNC_BLOCKED,
                    "[catalog] Release %s: %s refused: %s"
                        .formatted(event.release(), count(event.refused().size(), "item"), refused),
                    ResourceType.CATALOG_SYNC,
                    event.release())));
    TenantContext.setCurrentTenant(event.tenantId());
    try {
      tenantTx.execute(
          TxCtx.forTenant(event.tenantId()),
          () ->
              notificationDispatchService.alert(
                  event.tenantId(), userService.findAdministrators(event.tenantId()), group));
    } catch (Exception e) {
      log.error("Catalog sync alert failed for release {}", event.release(), e);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  // -- HELPERS --

  private void emit(String tenantId, Supplier<List<Emit>> work) {
    List<Emit> emits;
    TenantContext.setCurrentTenant(tenantId);
    try {
      emits = tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } catch (Exception e) {
      log.error("Content notification failed in tenant {}", tenantId, e);
      return;
    } finally {
      TenantContext.clearCurrentTenant();
    }
    emits.forEach(
        emit ->
            notificationEngineService.handleEventWithMessage(
                emit.entry(), emit.entityId(), tenantId, emit.type(), emit.message()));
  }

  private Map<String, List<PayloadUsageOutput>> byScenario(List<Payload> payloads) {
    return payloads.stream()
        .flatMap(payload -> payloadVersionChangeService.scenarioUsages(payload).stream())
        .collect(
            Collectors.groupingBy(
                PayloadUsageOutput::scenarioId, LinkedHashMap::new, Collectors.toList()));
  }

  private static String payloadLabel(Payload payload) {
    return payload.getName() + " v" + payload.getVersion();
  }

  private static String injects(int count) {
    return count(count, "inject");
  }

  private static String count(long count, String noun) {
    return count + " " + noun + (count == 1 ? "" : "s");
  }
}
