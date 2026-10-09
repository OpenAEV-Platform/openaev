package io.openaev.api.asset;

import static io.openaev.service.marking.MarkingEscalationValidator.assertCanAssignMarkings;

import io.openaev.api.asset.dto.AssetUpdateMarkingsInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.database.model.Action;
import io.openaev.database.model.Asset;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import jakarta.validation.constraints.NotBlank;
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

  private final AssetRepository assetRepository;
  private final MarkingDefinitionRepository markingDefinitionRepository;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  private final UserService userService;
  private final PermissionService permissionService;

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

    if (!removedIds.isEmpty()
        && !permissionService.hasCapabilityPermission(
            currentUser, ResourceType.MARKING_ASSIGNMENT, Action.DELETE)) {
      throw new ForbiddenException("Missing the DELETE_MARKING_ASSIGNMENT capability");
    }
    if (!addedIds.isEmpty()
        && !permissionService.hasCapabilityPermission(
            currentUser, ResourceType.MARKING_ASSIGNMENT, Action.WRITE)) {
      throw new ForbiddenException("Missing the ASSIGN_MARKING capability");
    }

    logDeclassification(asset, currentUser, previous, uniqueMarkingIds);

    asset.setMarkingIds(uniqueMarkingIds.toArray(String[]::new));
    return assetRepository.save(asset);
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
}
