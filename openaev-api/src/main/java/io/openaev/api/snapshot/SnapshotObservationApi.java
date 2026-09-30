package io.openaev.api.snapshot;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.snapshot.form.AttackObservationOutput;
import io.openaev.api.snapshot.form.SnapshotSearchInput;
import io.openaev.api.snapshot.form.SnapshotSearchOutput;
import io.openaev.api.snapshot.form.VulnerabilityObservationOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bulk differential export of observations for external GRC integrations (Story 1.7/1.8). Gated by
 * the {@link PreviewFeature#BULK_SNAPSHOT_EXPORT} preview flag (404 when off) and authorised solely
 * by {@code ACCESS_SNAPSHOT_OBSERVATION} — no {@code resourceId}, no {@code skipRBAC}.
 *
 * <p>The {@code TxCtx} parameters read as unused but are not: membership of {@code tenantId} is
 * already refused upstream by {@code TenantInterceptor}, but resolving {@code TxCtx} is what opens
 * the transaction with that tenant as its {@code app.current_tenants} scope. The pending-indexing
 * probe is a native query on tenant-active tables: without the scope it reads zero rows and would
 * report a ready snapshot while rows are still waiting to be indexed.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({SnapshotObservationApi.TENANT_SNAPSHOT_URI})
@Tag(name = "Snapshot API", description = "Bulk differential export of observations")
public class SnapshotObservationApi extends RestBehavior {

  public static final String TENANT_SNAPSHOT_URI = TENANT_PREFIX + "/snapshot";

  private final SnapshotObservationService snapshotObservationService;
  private final PreviewFeatureService previewFeatureService;

  @LogExecutionTime
  @PostMapping("/attack-observations/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SNAPSHOT_OBSERVATION)
  @Operation(summary = "Page attack observations for a bulk differential export")
  public SnapshotSearchOutput<AttackObservationOutput> searchAttackObservations(
      TxCtx ctx, @PathVariable String tenantId, @RequestBody @Valid SnapshotSearchInput input) {
    requireFeatureEnabled();
    return snapshotObservationService.searchAttackObservations(tenantId, input);
  }

  @LogExecutionTime
  @PostMapping("/vulnerability-observations/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SNAPSHOT_OBSERVATION)
  @Operation(summary = "Page vulnerability observations for a bulk differential export")
  public SnapshotSearchOutput<VulnerabilityObservationOutput> searchVulnerabilityObservations(
      TxCtx ctx, @PathVariable String tenantId, @RequestBody @Valid SnapshotSearchInput input) {
    requireFeatureEnabled();
    return snapshotObservationService.searchVulnerabilityObservations(tenantId, input);
  }

  /** FR39: the feature is invisible, not forbidden, when the flag is off. */
  private void requireFeatureEnabled() {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.BULK_SNAPSHOT_EXPORT)) {
      log.debug("Bulk snapshot export requested while the BULK_SNAPSHOT_EXPORT flag is disabled");
      throw new ElementNotFoundException("Not found");
    }
  }
}
