package io.openaev.api.asset;

import static io.openaev.api.markings.MarkingEscalationValidator.assertCanAssignMarkings;

import io.openaev.api.asset.dto.AssetUpdateMarkingsInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.database.model.Asset;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Arrays;
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
 * {@link io.openaev.api.markings.MarkingEscalationValidator}, and for the same reason — a boundary
 * you can widen for yourself is not a boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetMarkingsService {

  private final AssetRepository assetRepository;
  private final MarkingDefinitionRepository markingDefinitionRepository;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  private final UserService userService;

  /**
   * Replaces an asset's marking set.
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

    Set<String> previous =
        asset.getMarkingIds() == null ? Set.of() : Set.copyOf(Arrays.asList(asset.getMarkingIds()));
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
