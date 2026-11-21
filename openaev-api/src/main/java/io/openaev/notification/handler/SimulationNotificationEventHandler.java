package io.openaev.notification.handler;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.exposure.ExposureResult;
import io.openaev.service.exposure.ExposureResult.ReachedAsset;
import io.openaev.service.exposure.ExposureResult.Summary;
import io.openaev.service.exposure.ExposureResultService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * On a finished simulation, computes its exposure result and fires, through the notifications
 * engine, one {@code EXPOSURE_RESULT_READY} event and one {@code CRITICAL_ASSET_REACHED} event per
 * asset of high or very high criticality its attack path reached. The event message carries the
 * summary and the path of the exposure result endpoint; the resource id is the simulation id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationNotificationEventHandler implements NotificationEventHandler {

  static final String EXPOSURE_RESULT_PATH = "/api/exercises/%s/exposure-result";

  private final ExerciseService exerciseService;
  private final ExposureResultService exposureResultService;
  private final NotificationEngineService notificationEngineService;
  private final TenantScopedTransaction tenantTx;

  /** What the detection phase hands to the engine, outside its transaction. */
  record Outcome(String simulationId, String tenantId, ExposureResult result) {}

  @Override
  public void handle(NotificationEvent event) {
    if (!NotificationEventType.SIMULATION_FINISHED.equals(event.getEventType())
        || event.getTenantId() == null) {
      return;
    }
    // Same two phases as ScenarioNotificationEventHandler: the engine opens its own scoped
    // transaction per trigger and refuses to run inside an active one.
    Outcome outcome;
    TenantContext.setCurrentTenant(event.getTenantId());
    try {
      outcome =
          tenantTx.execute(
              TxCtx.forTenant(event.getTenantId()), () -> compute(event.getResourceId()));
    } finally {
      TenantContext.clearCurrentTenant();
    }
    if (outcome == null) {
      return;
    }
    notificationEngineService.handleEventWithMessage(
        NotificationResourceCatalog.SIMULATION,
        outcome.simulationId(),
        outcome.tenantId(),
        NotificationTriggerEventType.EXPOSURE_RESULT_READY,
        readyMessage(outcome.result()));
    for (ReachedAsset asset : criticalAssets(outcome.result())) {
      notificationEngineService.handleEventWithMessage(
          NotificationResourceCatalog.SIMULATION,
          outcome.simulationId(),
          outcome.tenantId(),
          NotificationTriggerEventType.CRITICAL_ASSET_REACHED,
          criticalMessage(outcome.result(), asset));
    }
  }

  private Outcome compute(String simulationId) {
    Exercise simulation;
    try {
      simulation = exerciseService.exercise(simulationId);
    } catch (RuntimeException e) {
      log.warn("Exposure result skipped: simulation {} not found", simulationId);
      return null;
    }
    if (simulation.getTenant() == null) {
      return null;
    }
    return new Outcome(
        simulation.getId(),
        simulation.getTenant().getId(),
        exposureResultService.compute(simulation));
  }

  /** Event announcing a finished simulation of a tenant. */
  public static NotificationEvent finishedEvent(Exercise simulation) {
    return NotificationEvent.builder()
        .eventType(NotificationEventType.SIMULATION_FINISHED)
        .resourceType(io.openaev.database.model.ResourceType.SIMULATION)
        .resourceId(simulation.getId())
        .tenantId(simulation.getTenant() != null ? simulation.getTenant().getId() : null)
        .timestamp(java.time.Instant.now())
        .build();
  }

  static List<ReachedAsset> criticalAssets(ExposureResult result) {
    return result.reachedAssets().stream().filter(ReachedAsset::critical).toList();
  }

  static String readyMessage(ExposureResult result) {
    Summary summary = result.summary();
    return "[simulation] "
        + result.simulationName()
        + " exposure result ready: "
        + summary.assetsExposed()
        + " of "
        + summary.assetsTested()
        + " assets exposed, "
        + summary.criticalAssetsReached()
        + " critical assets reached ("
        + EXPOSURE_RESULT_PATH.formatted(result.simulationId())
        + ")";
  }

  static String criticalMessage(ExposureResult result, ReachedAsset asset) {
    return "[simulation] "
        + result.simulationName()
        + " attack path reached "
        + asset.assetName()
        + " ("
        + asset.criticality()
        + ", asset "
        + asset.assetId()
        + ")";
  }
}
