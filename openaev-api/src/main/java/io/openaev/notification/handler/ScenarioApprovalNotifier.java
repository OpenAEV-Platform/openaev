package io.openaev.notification.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Inject;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Scenario;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the {@code APPROVAL_PENDING} and {@code APPROVED} scenario events through the notifications
 * engine (live triggers, webhook notifiers) once the transaction that changed the approval commits.
 *
 * <p>The message is a JSON document so an external orchestrator can turn a pending approval into a
 * decision without reading the scenario back: scenario id and name, safe mode, the held injects
 * with their asset groups, the asset group run order, the approval link, and for {@code APPROVED}
 * the approver and the time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioApprovalNotifier {

  /** Version of the message schema, raised on any breaking change of its fields. */
  public static final int MESSAGE_SCHEMA_VERSION = 1;

  private final ApplicationEventPublisher eventPublisher;
  private final NotificationEngineService notificationEngineService;
  private final ObjectMapper objectMapper;

  /** Published inside the transaction, delivered after its commit. */
  public record ScenarioApprovalEvent(
      String scenarioId, String tenantId, NotificationTriggerEventType eventType, String message) {}

  public void pending(
      @NotNull Scenario scenario, @NotNull Collection<Inject> heldInjects, String approveUrl) {
    publish(scenario, NotificationTriggerEventType.APPROVAL_PENDING, heldInjects, approveUrl);
  }

  public void approved(
      @NotNull Scenario scenario, @NotNull Collection<Inject> releasedInjects, String scenarioUrl) {
    publish(scenario, NotificationTriggerEventType.APPROVED, releasedInjects, scenarioUrl);
  }

  private void publish(
      Scenario scenario,
      NotificationTriggerEventType eventType,
      Collection<Inject> injects,
      String url) {
    String message = buildMessage(scenario, eventType, injects, url);
    String tenantId = scenario.getTenant() != null ? scenario.getTenant().getId() : null;
    eventPublisher.publishEvent(
        new ScenarioApprovalEvent(scenario.getId(), tenantId, eventType, message));
  }

  String buildMessage(
      Scenario scenario,
      NotificationTriggerEventType eventType,
      Collection<Inject> injects,
      String url) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("schema_version", MESSAGE_SCHEMA_VERSION);
    root.put("event", eventType.name());
    root.put("scenario_id", scenario.getId());
    root.put("scenario_name", scenario.getName());
    root.put("scenario_safe_mode", scenario.isSafeMode());
    root.put("scenario_approval_status", String.valueOf(scenario.getApprovalStatus()));
    if (scenario.getSecurityCoverage() != null) {
      root.put("security_coverage_external_id", scenario.getSecurityCoverage().getExternalId());
    }
    ArrayNode order = root.putArray("scenario_asset_group_order");
    if (scenario.getAssetGroupOrder() != null) {
      List.of(scenario.getAssetGroupOrder()).forEach(order::add);
    }
    ArrayNode injectNodes =
        root.putArray(
            NotificationTriggerEventType.APPROVED.equals(eventType)
                ? "released_injects"
                : "held_injects");
    for (Inject inject : injects) {
      ObjectNode node = injectNodes.addObject();
      node.put("inject_id", inject.getId());
      node.put("inject_title", inject.getTitle());
      ArrayNode groups = node.putArray("asset_groups");
      for (AssetGroup group : inject.getAssetGroups()) {
        ObjectNode g = groups.addObject();
        g.put("asset_group_id", group.getId());
        g.put("asset_group_name", group.getName());
      }
    }
    if (NotificationTriggerEventType.APPROVED.equals(eventType)) {
      root.put(
          "approved_by",
          scenario.getApprovedBy() != null ? scenario.getApprovedBy().getId() : null);
      root.put(
          "approved_at",
          scenario.getApprovedAt() != null ? scenario.getApprovedAt().toString() : null);
      root.put("scenario_url", url);
    } else {
      root.put("approve_url", url);
    }
    return root.toString();
  }

  @Async("notificationEngineExecutor")
  @TransactionalEventListener
  public void onScenarioApprovalEvent(ScenarioApprovalEvent event) {
    try {
      notificationEngineService.handleEventWithMessage(
          NotificationResourceCatalog.SCENARIO,
          event.scenarioId(),
          event.tenantId(),
          event.eventType(),
          event.message());
    } catch (Exception e) {
      log.error(
          "Failed to send {} for scenario {}: {}",
          event.eventType(),
          event.scenarioId(),
          e.getMessage(),
          e);
    }
  }
}
