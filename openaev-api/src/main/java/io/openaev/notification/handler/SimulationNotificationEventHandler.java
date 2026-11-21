package io.openaev.notification.handler;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.ResourceType;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Second handler of {@code SIMULATION_COMPLETED}, for the event sent on the simulation itself: when
 * the attack path of the finished simulation reached an asset of high or very high criticality, it
 * fires one {@code CRITICAL_ASSET_REACHED} event through the notifications engine.
 *
 * <p>Nothing is read unless a live trigger of the simulation's tenant listens to that event type:
 * with no such trigger, the handler stops at the trigger cache and the platform behaves as before.
 * The message names the simulation and the attack path route only, never an asset name or a count:
 * a reader opens the attack path with its own rights.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationNotificationEventHandler implements NotificationEventHandler {

  static final String ATTACK_PATH_ROUTE = "/api/attack-path/simulations/%s/graph";

  private static final String ASSET_NODE = "ASSET";
  private static final Set<String> CRITICAL =
      Set.of(AssetCriticality.HIGH.name(), AssetCriticality.VERY_HIGH.name());

  private final ExerciseService exerciseService;
  private final AttackPathGraphService attackPathGraphService;
  private final NotificationEngineService notificationEngineService;
  private final TenantScopedTransaction tenantTx;

  /** Delay before the event is handled, so the transaction that finished the run has committed. */
  public static final long COMMIT_DELAY_SECONDS = 5;

  /**
   * The {@code SIMULATION_COMPLETED} event sent on the simulation itself (resource SIMULATION),
   * next to the one sent on its scenario.
   */
  public static NotificationEvent completedEvent(Exercise simulation) {
    return NotificationEvent.builder()
        .eventType(NotificationEventType.SIMULATION_COMPLETED)
        .resourceType(ResourceType.SIMULATION)
        .resourceId(simulation.getId())
        .tenantId(simulation.getTenant() != null ? simulation.getTenant().getId() : null)
        .timestamp(Instant.now())
        .build();
  }

  /** What the detection phase hands to the engine, outside its transaction. */
  record Reached(String simulationId, String tenantId, String simulationName) {}

  @Override
  public void handle(NotificationEvent event) {
    if (!NotificationEventType.SIMULATION_COMPLETED.equals(event.getEventType())
        || event.getTenantId() == null
        || !notificationEngineService.hasLiveTrigger(
            NotificationResourceCatalog.SIMULATION,
            event.getTenantId(),
            NotificationTriggerEventType.CRITICAL_ASSET_REACHED)) {
      return;
    }
    // Same two phases as ScenarioNotificationEventHandler: the engine opens its own scoped
    // transaction per trigger and refuses to run inside an active one. The tenant comes from the
    // event through the TxCtx, never from the per-thread tenant.
    TxCtx ctx = TxCtx.forTenant(event.getTenantId());
    Reached reached = tenantTx.execute(ctx, () -> detect(ctx, event.getResourceId()));
    if (reached == null) {
      return;
    }
    notificationEngineService.handleEventWithMessage(
        NotificationResourceCatalog.SIMULATION,
        reached.simulationId(),
        reached.tenantId(),
        NotificationTriggerEventType.CRITICAL_ASSET_REACHED,
        message(reached));
  }

  private Reached detect(TxCtx ctx, String simulationId) {
    Exercise simulation = exerciseService.findByIdInScope(ctx, simulationId).orElse(null);
    if (simulation == null || simulation.getTenant() == null) {
      return null;
    }
    if (!reachesCriticalAsset(simulationId)) {
      return null;
    }
    return new Reached(simulation.getId(), simulation.getTenant().getId(), simulation.getName());
  }

  /** True when an asset node of the run's attack path has a high or very high criticality. */
  private boolean reachesCriticalAsset(String simulationId) {
    try {
      AttackPathDTO graph = attackPathGraphService.buildGraph(simulationId);
      if (graph == null || graph.attackPathNodes() == null) {
        return false;
      }
      for (AttackPathNodeDTO node : graph.attackPathNodes()) {
        if (ASSET_NODE.equals(node.getType())
            && node.getEntityKind() == null
            && node.getCriticality() != null
            && CRITICAL.contains(node.getCriticality())) {
          return true;
        }
      }
      return false;
    } catch (RuntimeException e) {
      // The attack path is optional: a run without it reaches nothing.
      log.warn("Attack path unavailable for simulation {}", simulationId, e);
      return false;
    }
  }

  static String message(Reached reached) {
    return "[simulation] "
        + reached.simulationName()
        + ": attack path reached a critical asset ("
        + ATTACK_PATH_ROUTE.formatted(reached.simulationId())
        + ")";
  }
}
