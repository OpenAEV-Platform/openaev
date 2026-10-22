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
import io.openaev.rest.asset.form.AssetBulkProcessingInput;
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

/**
 * Writes the markings carried by an asset (design step 3.3).
 *
 * <p>The counterpart to {@code TenantGroupService.updateGroupMarkings}: that one grants a
 * <i>clearance</i> to a group, this one puts a <i>label</i> on a row. Both go through the same
 * {@link io.openaev.service.marking.MarkingEscalationValidator} and the same diff-based {@code
 * ASSIGN_MARKING}/{@code DELETE_MARKING_ASSIGNMENT} capability check, for the same reason — a
 * boundary you can widen for yourself is not a boundary.
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
   *   <li><b>Capability, per direction actually used.</b> Computed from the diff between the
   *       payload and the asset's current markings (read before the array is overwritten): removing
   *       a currently-carried marking requires {@code DELETE_MARKING_ASSIGNMENT}, adding one not
   *       currently carried requires {@code ASSIGN_MARKING} - either, both, or neither, depending
   *       on what this particular payload changes. This is additive to the asset's own {@code
   *       WRITE} control checked at the API layer, not a replacement for it.
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

    assertMarkingDirectionCapability(currentUser, !removedIds.isEmpty(), !addedIds.isEmpty());

    logDeclassification(asset, currentUser, previous, uniqueMarkingIds);

    markingMetricCollector.recordAssetMarkingsAssigned(addedIds.size());
    markingMetricCollector.recordAssetMarkingsUnassigned(removedIds.size());

    asset.setMarkingIds(uniqueMarkingIds.toArray(String[]::new));
    return assetRepository.save(asset);
  }

  /**
   * Checks the per-direction {@code ASSIGN_MARKING}/{@code DELETE_MARKING_ASSIGNMENT} capability
   * against a diff already computed by the caller - shared by the single-asset path, the bulk
   * upfront scan, and the bulk per-chunk re-check, so the two capabilities this rule is actually
   * gated on live in exactly one place.
   */
  private void assertMarkingDirectionCapability(
      User currentUser, boolean anyRemoved, boolean anyAdded) {
    if (anyRemoved
        && !permissionService.hasCapabilityPermission(
            currentUser, ResourceType.MARKING_ASSIGNMENT, Action.DELETE)) {
      throw new ForbiddenException("Missing the DELETE_MARKING_ASSIGNMENT capability");
    }
    if (anyAdded
        && !permissionService.hasCapabilityPermission(
            currentUser, ResourceType.MARKING_ASSIGNMENT, Action.WRITE)) {
      throw new ForbiddenException("Missing the ASSIGN_MARKING capability");
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
   * Replaces the whole marking set of every asset in a bulk selection (explicit ids, or a search
   * input with optional exclusions — the same select-all pattern as {@code
   * AssetService.bulkDeleteAssets}), the bulk mirror of {@link #updateAssetMarkings}.
   *
   * <p>Every asset in scope ends up carrying exactly the requested set, nothing kept and nothing
   * merged. Capability is gated by the same per-direction rule as {@link #updateAssetMarkings} -
   * {@code DELETE_MARKING_ASSIGNMENT} only if some asset in scope would lose a marking, {@code
   * ASSIGN_MARKING} only if some asset in scope would gain one - except aggregated over the whole
   * scope rather than per asset: {@link #findBulkDiffDirections} reads scoped assets' current
   * markings upfront (stopping early once both directions are confirmed), so the gate can still
   * fail closed before any chunk writes rather than discovering a missing capability mid-write on
   * some later chunk. That upfront read is a snapshot from its own, already-committed short
   * transaction, so each chunk's write re-derives the diff under a {@code SELECT ... FOR UPDATE}
   * row lock ({@link io.openaev.database.repository.AssetRepository#findAllByIdForUpdate}) and
   * re-checks the capability against that fresh state before applying anything - a concurrent
   * request that changes one of these assets' markings between the upfront scan and this chunk's
   * write cannot smuggle a direction past the capability this caller actually holds.
   *
   * <p>Escalation is still checked once, upfront, against the requested final set - same as {@link
   * #updateAssetMarkings}: a caller may not grant a marking they do not hold, whatever an asset's
   * prior state. Declassification is logged per asset, same as the single-asset endpoint.
   *
   * <p>Callers must NOT be transactional: the escalation/capability check and the scope resolution
   * each run in their own short transaction (through {@code chunkRunner}, which also enables the
   * tenant filter), and the matched assets are then updated chunk by chunk in independent
   * transactions, the same shape {@code ExpectationsDriftService.realign} uses for the same reason
   * — a single transaction spanning a large bulk scope would hold row locks for its whole duration.
   *
   * @param tenantId the tenant whose clearance the caller is checked against
   * @return the ids of the assets whose markings were updated
   */
  public List<String> bulkReplaceAssetMarkings(
      final TxCtx ctx,
      @NotBlank final String tenantId,
      @NotNull final AssetBulkReplaceMarkingsInput input) {
    Set<String> uniqueMarkingIds = new LinkedHashSet<>(input.markingIds());

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

    AssetBulkProcessingInput selection = new AssetBulkProcessingInput();
    selection.setSearchPaginationInput(input.searchPaginationInput());
    selection.setAssetIdsToProcess(input.assetIdsToProcess());
    selection.setAssetIdsToIgnore(input.assetIdsToIgnore());
    List<String> assetIds = assetService.resolveBulkAssetIds(ctx, selection);
    if (assetIds.isEmpty()) {
      return List.of();
    }
    // Deterministic order: two concurrent bulk operations with overlapping scopes acquire their row
    // locks in the same order instead of deadlocking each other.
    List<String> sortedIds = assetIds.stream().sorted().toList();

    DiffDirections directions = findBulkDiffDirections(ctx, sortedIds, uniqueMarkingIds);
    assertMarkingDirectionCapability(currentUser, directions.anyRemoved(), directions.anyAdded());

    String operationId = bulkOperationMonitor.start("replace-markings", "assets", sortedIds.size());
    try {
      for (int start = 0; start < sortedIds.size(); start += CHUNK_SIZE) {
        List<String> chunk =
            sortedIds.subList(start, Math.min(start + CHUNK_SIZE, sortedIds.size()));
        // The suppression scope wraps the transaction proxy call (not just the work): lifecycle
        // events also fire during the commit-time flush, inside chunkRunner.call.
        // The upfront directions/capability check (above) ran against a snapshot read in its own,
        // already-committed short transaction - a concurrent request can change one of these
        // assets' markings between that read and this chunk's write. findAllByIdForUpdate takes a
        // row lock on this chunk before re-deriving the diff from the now-guaranteed-fresh state,
        // so
        // the capability actually checked against is the one this write would really need, not the
        // one it would have needed when the upfront scan ran.
        BulkOperationContext.runSuppressed(
            () ->
                chunkRunner.call(
                    ctx,
                    () -> {
                      List<Asset> assets =
                          fromIterable(assetRepository.findAllByIdForUpdate(chunk));
                      List<Set<String>> previousByAsset = new ArrayList<>(assets.size());
                      boolean chunkAnyRemoved = false;
                      boolean chunkAnyAdded = false;
                      for (Asset asset : assets) {
                        Set<String> previous =
                            asset.getMarkingIds() == null
                                ? Set.of()
                                : Set.copyOf(Arrays.asList(asset.getMarkingIds()));
                        previousByAsset.add(previous);
                        chunkAnyRemoved =
                            chunkAnyRemoved || !uniqueMarkingIds.containsAll(previous);
                        chunkAnyAdded = chunkAnyAdded || !previous.containsAll(uniqueMarkingIds);
                      }
                      assertMarkingDirectionCapability(currentUser, chunkAnyRemoved, chunkAnyAdded);

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
        // BulkDeleteExecutor.deleteInChunks.
        entityManager.clear();
        bulkOperationMonitor.progress(operationId, chunk.size());
      }
      bulkOperationMonitor.complete(operationId);
    } catch (RuntimeException e) {
      bulkOperationMonitor.fail(operationId);
      throw e;
    }
    return sortedIds;
  }

  /** Whether any asset in a bulk-replace scope would lose, respectively gain, a marking. */
  private record DiffDirections(boolean anyRemoved, boolean anyAdded) {}

  /**
   * Aggregates the add/remove direction of a bulk replace across every asset in {@code assetIds},
   * scanned in the same bounded {@link #CHUNK_SIZE} batches as the write loop above rather than one
   * unbounded {@code findAllById}. Endpoints' eagerly-fetched agent collection ({@code
   * Endpoint.agents}, {@code FetchMode.SUBSELECT}) means a single {@code findAllById} over a large
   * select-all scope hydrates every matched endpoint's full agent list into the persistence context
   * before the per-asset early-exit below ever runs - the short-circuit only skips *iterating* the
   * list that is already fully loaded, not loading it. Scanning batch by batch, each in its own
   * short transaction with the persistence context cleared afterward (same pattern as {@link
   * io.openaev.service.utils.BulkDeleteExecutor#deleteInChunks}), bounds the entities held in
   * memory to one batch and lets a scope where both directions already show up in the first batch
   * skip loading the rest entirely.
   *
   * <p>Loads full entities rather than a {@code [id, markingIds]} projection: {@code markingIds} is
   * a {@code text[]} column mapped through a custom Hibernate {@code UserType} ({@code
   * StringArrayType}), and this codebase has no precedent of that type surviving a multi-column
   * JPQL tuple projection correctly - only ever through full entity hydration (as the chunked write
   * loop below already does). Loading the scope twice (here, and again per chunk during the write)
   * costs more than a projection would, but a wrong cast here would silently mis-gate a security
   * check, which costs more than the extra read.
   */
  private DiffDirections findBulkDiffDirections(
      TxCtx ctx, List<String> assetIds, Set<String> requested) {
    boolean anyRemoved = false;
    boolean anyAdded = false;
    for (int start = 0; start < assetIds.size(); start += CHUNK_SIZE) {
      List<String> batch = assetIds.subList(start, Math.min(start + CHUNK_SIZE, assetIds.size()));
      DiffDirections batchDirections =
          chunkRunner.call(ctx, () -> findBatchDiffDirections(batch, requested));
      entityManager.clear();
      anyRemoved = anyRemoved || batchDirections.anyRemoved();
      anyAdded = anyAdded || batchDirections.anyAdded();
      if (anyRemoved && anyAdded) {
        break;
      }
    }
    return new DiffDirections(anyRemoved, anyAdded);
  }

  private DiffDirections findBatchDiffDirections(List<String> assetIds, Set<String> requested) {
    boolean anyRemoved = false;
    boolean anyAdded = false;
    for (Asset asset : fromIterable(assetRepository.findAllById(assetIds))) {
      Set<String> previous =
          asset.getMarkingIds() == null
              ? Set.of()
              : Set.copyOf(Arrays.asList(asset.getMarkingIds()));
      anyRemoved = anyRemoved || !requested.containsAll(previous);
      anyAdded = anyAdded || !previous.containsAll(requested);
      if (anyRemoved && anyAdded) {
        break;
      }
    }
    return new DiffDirections(anyRemoved, anyAdded);
  }
}
