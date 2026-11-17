package io.openaev.api.asset;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.marking.MarkingEscalationValidator.assertCanAssignMarkings;

import io.openaev.api.asset.dto.AssetBulkReplaceMarkingsInput;
import io.openaev.api.asset.dto.AssetUpdateMarkingsInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Asset;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.service.AssetService;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.utils.BulkDeleteExecutor;
import io.openaev.telemetry.metric_collectors.MarkingMetricCollector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the markings carried by an asset (design step 3.3).
 *
 * <p>The counterpart to {@code TenantGroupService.updateGroupMarkings}: that one grants a
 * <i>clearance</i> to a group, this one puts a <i>label</i> on a row. Both go through the same
 * {@link io.openaev.service.marking.MarkingEscalationValidator} and the same unconditional {@code
 * MANAGE_MARKING_ASSIGNMENT} capability check, for the same reason — a boundary you can widen for
 * yourself is not a boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetMarkingsService {

  private final AssetRepository assetRepository;
  private final MarkingDefinitionRepository markingDefinitionRepository;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  private final UserService userService;
  private final PermissionService permissionService;
  private final MarkingMetricCollector markingMetricCollector;
  private final AssetService assetService;
  private final BulkDeleteExecutor bulkDeleteExecutor;

  /**
   * Replaces an asset's marking set, behind three guards: existence/tenant (a marking from another
   * tenant never comes back, so a mismatch is a 404, not a silent partial write), escalation
   * ({@link io.openaev.service.marking.MarkingEscalationValidator} refuses assigning a marking you
   * don't hold, so "can write assets" never implies "can read every marked row"), and capability
   * ({@code MANAGE_MARKING_ASSIGNMENT}, required unconditionally - the same upfront shape as {@link
   * #bulkReplaceAssetMarkings} and {@code AssetService.bulkDeleteAssets}).
   *
   * <p>No cache eviction here: the marking-set predicate re-reads the row on every query, only the
   * clearance itself is cached, so evicting on a write would be a no-op that looks like protection.
   * Eviction only matters when a clearance shrinks.
   *
   * <p>Self-lockout is impossible by construction, not by a separate check: since {@code requested
   * ⊆ clearance} and visibility is {@code row_markings ⊆ clearance}, the asset you just marked
   * stays readable by you.
   *
   * @param tenantId the tenant whose clearance and capabilities the caller is checked against
   */
  @Transactional(rollbackFor = Exception.class)
  public Asset updateAssetMarkings(
      @NotBlank final String tenantId,
      @NotBlank final String assetId,
      final AssetUpdateMarkingsInput input) {
    // Tenant-scoped lookup: a plain findById skips Hibernate's filters, and once marking-active,
    // the statement inspector too - so an unreadable asset here is a 404, not a declassification.
    Asset asset =
        assetRepository
            .findByIdAndTenantId(assetId, tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Asset not found: " + assetId));

    Set<String> uniqueMarkingIds = new LinkedHashSet<>(input.markingIds());
    List<MarkingDefinition> markings = new ArrayList<>();
    markingDefinitionRepository
        .findAllByIdInTenant(uniqueMarkingIds, tenantId)
        .forEach(markings::add);
    if (markings.size() != uniqueMarkingIds.size()) {
      throw new ElementNotFoundException(
          "One or more marking definitions not found in the current tenant");
    }

    User currentUser = userService.currentUser();
    assertCanAssignMarkings(
        markingClearanceCacheManager.findClearance(currentUser.getId(), tenantId), markings);
    assertMarkingAssignmentCapability(tenantId, currentUser);

    Set<String> removed = applyAssetMarkings(asset, uniqueMarkingIds);
    Asset saved = assetRepository.save(asset);
    // Only removals are logged (design §4.3): adding a marking narrows who can read the asset and
    // needs no explaining, while removing one widens it, and widening is the direction that turns
    // into an incident. Logged rather than raised as a domain event because the platform has no
    // audit-event facility yet - when one lands this is the single call site to move.
    if (!removed.isEmpty()) {
      log.warn(
          "Marking declassification: asset={} actor={} removedMarkings={} remainingMarkings={}",
          asset.getId(),
          currentUser.getId(),
          removed,
          uniqueMarkingIds);
    }
    return saved;
  }

  /**
   * Checks {@code MANAGE_MARKING_ASSIGNMENT}, read as granted in {@code tenantId} - the tenant of
   * the asset being written, not necessarily the caller's ambient tenant (see {@link
   * PermissionService#holdsInTenant}).
   */
  private void assertMarkingAssignmentCapability(String tenantId, User currentUser) {
    boolean hasCapability =
        permissionService.holdsInTenant(
            tenantId,
            () ->
                permissionService.hasCapabilityPermission(
                    currentUser, ResourceType.MARKING_ASSIGNMENT, Action.WRITE));
    if (!hasCapability) {
      throw new ForbiddenException("Missing the MANAGE_MARKING_ASSIGNMENT capability");
    }
  }

  /**
   * Writes {@code requested} to {@code asset}, records the assign/unassign metrics, and returns the
   * markings that were removed, for the caller to log - the one place both {@link
   * #updateAssetMarkings} and {@link #bulkReplaceAssetMarkings} apply a replace, so they cannot
   * drift apart.
   */
  private Set<String> applyAssetMarkings(Asset asset, Set<String> requested) {
    Set<String> previous =
        asset.getMarkingIds() == null ? Set.of() : Set.copyOf(Arrays.asList(asset.getMarkingIds()));
    Set<String> added = new HashSet<>(requested);
    added.removeAll(previous);
    Set<String> removed = new HashSet<>(previous);
    removed.removeAll(requested);
    asset.setMarkingIds(requested.toArray(String[]::new));
    markingMetricCollector.recordAssetMarkingsAssigned(added.size());
    markingMetricCollector.recordAssetMarkingsUnassigned(removed.size());
    return removed;
  }

  /**
   * Bulk mirror of {@link #updateAssetMarkings}, chunk by chunk via {@link
   * BulkDeleteExecutor#processInChunks} - the same sorted-chunk / deadlock-retry / progress /
   * session-clear machinery the other bulk operations use, generalized past deletes.
   *
   * <p>Same shape as the single-asset path and the other bulk operations' capability gate (e.g.
   * {@code AssetService.bulkDeleteAssets}): {@code MANAGE_MARKING_ASSIGNMENT} is required
   * unconditionally, upfront. With no later conditional recheck, a chunk needs no row lock either:
   * plain {@code findAllById} is enough.
   *
   * @param tenantId the tenant whose clearance the caller is checked against
   * @return the ids of the assets whose markings were updated
   */
  public List<String> bulkReplaceAssetMarkings(
      final TxCtx ctx,
      @NotBlank final String tenantId,
      @NotNull final AssetBulkReplaceMarkingsInput input) {
    Set<String> uniqueMarkingIds = new LinkedHashSet<>(input.getMarkingIds());

    List<MarkingDefinition> markings =
        bulkDeleteExecutor.resolveInTransaction(
            ctx,
            () -> {
              List<MarkingDefinition> found = new ArrayList<>();
              markingDefinitionRepository
                  .findAllByIdInTenant(uniqueMarkingIds, tenantId)
                  .forEach(found::add);
              if (found.size() != uniqueMarkingIds.size()) {
                throw new ElementNotFoundException(
                    "One or more marking definitions not found in the current tenant");
              }
              return found;
            });

    User currentUser = userService.currentUser();
    assertCanAssignMarkings(
        markingClearanceCacheManager.findClearance(currentUser.getId(), tenantId), markings);
    assertMarkingAssignmentCapability(tenantId, currentUser);

    List<String> assetIds = assetService.resolveBulkAssetIds(ctx, input);

    return bulkDeleteExecutor.processInChunks(
        ctx,
        "update",
        "assets",
        assetIds,
        chunk -> {
          List<Asset> assets = fromIterable(assetRepository.findAllById(chunk));
          List<String> declassified = new ArrayList<>();
          for (Asset asset : assets) {
            if (!applyAssetMarkings(asset, uniqueMarkingIds).isEmpty()) {
              declassified.add(asset.getId());
            }
          }
          assetRepository.saveAll(assets);
          if (!declassified.isEmpty()) {
            log.warn(
                "Marking declassification: actor={} assets={} remainingMarkings={}",
                currentUser.getId(),
                declassified,
                uniqueMarkingIds);
          }
        });
  }
}
