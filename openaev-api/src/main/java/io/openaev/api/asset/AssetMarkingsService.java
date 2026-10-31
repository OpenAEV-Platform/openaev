package io.openaev.api.asset;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.marking.MarkingEscalationValidator.assertCanAssignMarkings;

import io.openaev.api.asset.dto.AssetBulkReplaceMarkingsInput;
import io.openaev.api.asset.dto.AssetUpdateMarkingsInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.BulkOperationContext;
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
import io.openaev.service.utils.BulkDeleteChunkRunner;
import io.openaev.service.utils.BulkOperationMonitor;
import io.openaev.telemetry.metric_collectors.MarkingMetricCollector;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Writes the markings carried by an asset (design step 3.3).
 *
 * <p>The counterpart to {@code TenantGroupService.updateGroupMarkings}: that one grants a
 * <i>clearance</i> to a group, this one puts a <i>label</i> on a row. Both go through the same
 * {@link io.openaev.service.marking.MarkingEscalationValidator} and the same diff-based {@code
 * MANAGE_MARKING_ASSIGNMENT} capability check, for the same reason — a boundary you can widen for
 * yourself is not a boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetMarkingsService {

  /** Small chunks keep each chunk's transaction - and the row locks it holds - short. */
  private static final int CHUNK_SIZE = 25;

  private final AssetRepository assetRepository;
  private final MarkingDefinitionRepository markingDefinitionRepository;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  private final UserService userService;
  private final PermissionService permissionService;
  private final MarkingMetricCollector markingMetricCollector;
  private final AssetService assetService;
  private final BulkOperationMonitor bulkOperationMonitor;
  private final BulkDeleteChunkRunner chunkRunner;
  private final EntityManager entityManager;

  /**
   * Replaces an asset's marking set.
   *
   * <p>Three guards, in this order:
   *
   * <ol>
   *   <li><b>Existence and tenant.</b> {@code marking_definitions} is a tenant-active table, so the
   *       statement inspector already restricts this read to the request scope: a marking from
   *       another tenant simply does not come back, and the size check turns that into a 404 rather
   *       than a silent partial assignment.
   *   <li><b>Escalation.</b> {@link io.openaev.service.marking.MarkingEscalationValidator} — you
   *       may not assign what you do not hold. Without it, "may write assets" would quietly mean
   *       "may read every marked row".
   *   <li><b>Capability, only if the set actually changes.</b> Any add or remove requires {@code
   *       MANAGE_MARKING_ASSIGNMENT}, additive to the asset's own {@code WRITE} control.
   * </ol>
   *
   * <p>🔴 <b>No cache eviction here, deliberately.</b> The rewritten predicate is {@code
   * is_marking_set_allowed(marking_ids)} — the row's array is a function <i>argument</i>, re-read
   * on every query; only the <i>clearance</i> lives in the cached GUC. Evicting on an asset write
   * would be a no-op that <i>looks</i> like protection, which is worse than none: the next reader
   * would assume a coverage that was never there. Eviction belongs only where a clearance shrinks
   * (group membership, grant removal, definition delete, order lowered).
   *
   * <p><b>Self-lockout is impossible by construction</b>, which is why there is no separate check
   * for it. The validator enforces {@code requested ⊆ your clearance}, and a row is visible iff
   * {@code row_markings ⊆ clearance}; so the asset you just marked is still readable by you. The
   * same guard that stops escalation stops the lockout.
   *
   * @param tenantId the tenant whose clearance the caller is checked against
   */
  @Transactional(rollbackFor = Exception.class)
  public Asset updateAssetMarkings(
      @NotBlank final String tenantId,
      @NotBlank final String assetId,
      final AssetUpdateMarkingsInput input) {
    // Tenant-scoped lookup: a plain findById bypasses Hibernate's entity filters on a primary-key
    // load. Once `assets` is marking-active the statement inspector also hides rows above the
    // caller's clearance, so an asset they may not read is a 404 here - they cannot declassify what
    // they cannot see.
    Asset asset =
        assetRepository
            .findByIdAndTenantId(assetId, tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Asset not found: " + assetId));

    Set<String> uniqueMarkingIds = new LinkedHashSet<>(input.markingIds());
    List<MarkingDefinition> markings = new ArrayList<>();
    markingDefinitionRepository.findAllById(uniqueMarkingIds).forEach(markings::add);
    if (markings.size() != uniqueMarkingIds.size()) {
      throw new ElementNotFoundException(
          "One or more marking definitions not found in the current tenant");
    }

    User currentUser = userService.currentUser();
    assertCanAssignMarkings(
        markingClearanceCacheManager.findClearance(
            currentUser.getId(), tenantId, currentUser.isAdminOrBypass()),
        markings);

    // Read before the marking_ids array is overwritten below - this is the "before" side of the
    // diff.
    Set<String> previous =
        asset.getMarkingIds() == null ? Set.of() : Set.copyOf(Arrays.asList(asset.getMarkingIds()));
    Set<String> removedIds = new HashSet<>(previous);
    removedIds.removeAll(uniqueMarkingIds);
    Set<String> addedIds = new HashSet<>(uniqueMarkingIds);
    addedIds.removeAll(previous);

    assertMarkingAssignmentCapability(currentUser, !removedIds.isEmpty() || !addedIds.isEmpty());

    logDeclassification(asset, currentUser, previous, uniqueMarkingIds);

    markingMetricCollector.recordAssetMarkingsAssigned(addedIds.size());
    markingMetricCollector.recordAssetMarkingsUnassigned(removedIds.size());

    asset.setMarkingIds(uniqueMarkingIds.toArray(String[]::new));
    return assetRepository.save(asset);
  }

  /** Checks {@code MANAGE_MARKING_ASSIGNMENT} when a precomputed diff shows an actual change. */
  private void assertMarkingAssignmentCapability(User currentUser, boolean anyChanged) {
    if (anyChanged
        && !permissionService.hasCapabilityPermission(
            currentUser, ResourceType.MARKING_ASSIGNMENT, Action.WRITE)) {
      throw new ForbiddenException("Missing the MANAGE_MARKING_ASSIGNMENT capability");
    }
  }

  /**
   * Records every marking <i>removal</i> and nothing else (design §4.3).
   *
   * <p>Only removals: adding a marking narrows who can read the asset and needs no explaining,
   * while removing one widens it, and widening is the direction that turns into an incident. Logged
   * rather than raised as a domain event because the platform has no audit-event facility yet —
   * when one lands this is the single call site to move.
   */
  private void logDeclassification(
      Asset asset, User actor, Set<String> previous, Set<String> requested) {
    List<String> removed =
        previous.stream().filter(id -> !requested.contains(id)).sorted().toList();
    if (removed.isEmpty()) {
      return;
    }
    log.warn(
        "Marking declassification: asset={} actor={} removedMarkings={} remainingMarkings={}",
        asset.getId(),
        actor.getId(),
        removed,
        requested);
  }

  /**
   * Bulk mirror of {@link #updateAssetMarkings}, chunk by chunk, re-verifying capability and
   * escalation per chunk under a row lock.
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
        chunkRunner.call(
            ctx,
            () -> {
              List<MarkingDefinition> found = new ArrayList<>();
              markingDefinitionRepository.findAllById(uniqueMarkingIds).forEach(found::add);
              if (found.size() != uniqueMarkingIds.size()) {
                throw new ElementNotFoundException(
                    "One or more marking definitions not found in the current tenant");
              }
              return found;
            });

    User currentUser = userService.currentUser();
    assertCanAssignMarkings(
        markingClearanceCacheManager.findClearance(
            currentUser.getId(), tenantId, currentUser.isAdminOrBypass()),
        markings);

    List<String> assetIds = assetService.resolveBulkAssetIds(ctx, input);
    if (assetIds.isEmpty()) {
      return List.of();
    }
    // Deterministic order: two concurrent bulk operations with overlapping scopes acquire their row
    // locks in the same order instead of deadlocking each other.
    List<String> sortedIds = assetIds.stream().sorted().toList();

    // See the class javadoc above: only a chunk that actually commits on its own may have its
    // persistence context cleared afterward - a chunk nested inside an already-open transaction
    // (e.g. a transactional test) has not flushed yet, and clearing would silently drop its write.
    boolean clearBetweenChunks = !TransactionSynchronizationManager.isActualTransactionActive();

    boolean anyChanged =
        findBulkMarkingChange(ctx, sortedIds, uniqueMarkingIds, clearBetweenChunks);
    assertMarkingAssignmentCapability(currentUser, anyChanged);

    String operationId = bulkOperationMonitor.start("replace-markings", "assets", sortedIds.size());
    try {
      for (int start = 0; start < sortedIds.size(); start += CHUNK_SIZE) {
        List<String> chunk =
            sortedIds.subList(start, Math.min(start + CHUNK_SIZE, sortedIds.size()));
        // The suppression scope wraps the transaction proxy call (not just the work): lifecycle
        // events also fire during the commit-time flush, inside chunkRunner.call.
        // findAllByIdForUpdate row-locks this chunk so the capability re-check below sees fresh
        // state, not the upfront scan's now-possibly-stale snapshot.
        BulkOperationContext.runSuppressed(
            () ->
                chunkRunner.call(
                    ctx,
                    () -> {
                      List<Asset> assets =
                          fromIterable(assetRepository.findAllByIdForUpdate(chunk));
                      List<Set<String>> previousByAsset = new ArrayList<>(assets.size());
                      boolean chunkAnyChanged = false;
                      for (Asset asset : assets) {
                        Set<String> previous =
                            asset.getMarkingIds() == null
                                ? Set.of()
                                : Set.copyOf(Arrays.asList(asset.getMarkingIds()));
                        previousByAsset.add(previous);
                        chunkAnyChanged = chunkAnyChanged || !previous.equals(uniqueMarkingIds);
                      }
                      assertMarkingAssignmentCapability(currentUser, chunkAnyChanged);

                      int added = 0;
                      int removed = 0;
                      for (int i = 0; i < assets.size(); i++) {
                        Asset asset = assets.get(i);
                        Set<String> previous = previousByAsset.get(i);
                        Set<String> removedIds = new HashSet<>(previous);
                        removedIds.removeAll(uniqueMarkingIds);
                        Set<String> addedIds = new HashSet<>(uniqueMarkingIds);
                        addedIds.removeAll(previous);

                        logDeclassification(asset, currentUser, previous, uniqueMarkingIds);
                        asset.setMarkingIds(uniqueMarkingIds.toArray(String[]::new));
                        added += addedIds.size();
                        removed += removedIds.size();
                      }
                      markingMetricCollector.recordAssetMarkingsAssigned(added);
                      markingMetricCollector.recordAssetMarkingsUnassigned(removed);
                      return fromIterable(assetRepository.saveAll(assets));
                    }));
        // With open-session-in-view the request session outlives each chunk transaction: clear it
        // so updated assets (and their eagerly-fetched agents, see Endpoint.agents) do not
        // accumulate in the persistence context across a large bulk - same as
        // BulkDeleteExecutor.deleteInChunks. Skipped when nested in an already-open transaction
        // (see clearBetweenChunks above): that chunk never actually committed, so its write is
        // still unflushed and clearing now would discard it instead of merely detaching it.
        if (clearBetweenChunks) {
          entityManager.clear();
        }
        bulkOperationMonitor.progress(operationId, chunk.size());
      }
      bulkOperationMonitor.complete(operationId);
    } catch (RuntimeException e) {
      bulkOperationMonitor.fail(operationId);
      throw e;
    }
    return sortedIds;
  }

  /** Whether any asset in the scope would have its marking set change, scanned batch by batch. */
  private boolean findBulkMarkingChange(
      TxCtx ctx, List<String> assetIds, Set<String> requested, boolean clearBetweenChunks) {
    for (int start = 0; start < assetIds.size(); start += CHUNK_SIZE) {
      List<String> batch = assetIds.subList(start, Math.min(start + CHUNK_SIZE, assetIds.size()));
      boolean batchChanged = chunkRunner.call(ctx, () -> findBatchMarkingChange(batch, requested));
      if (clearBetweenChunks) {
        entityManager.clear();
      }
      if (batchChanged) {
        return true;
      }
    }
    return false;
  }

  private boolean findBatchMarkingChange(List<String> assetIds, Set<String> requested) {
    for (Asset asset : fromIterable(assetRepository.findAllById(assetIds))) {
      Set<String> previous =
          asset.getMarkingIds() == null
              ? Set.of()
              : Set.copyOf(Arrays.asList(asset.getMarkingIds()));
      if (!previous.equals(requested)) {
        return true;
      }
    }
    return false;
  }
}
