package io.openaev.service.finding;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.FindingStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Lifecycle of a finding: re-tests on demand and the status they set.
 *
 * <p>A re-test queues a copy of the inject that last produced the finding (same contract, content
 * and targets, so the same mode as the original run: a run limited to read-only contracts stays so)
 * and keeps the original. When the copy finishes, {@link FindingRetestSettlement} moves the status:
 * {@link FindingStatus#OPEN} if the copy detected it again ({@link #markDetected}, called by the
 * recording path), else {@link FindingStatus#NOT_REPRODUCED} with the copy as the run that set it.
 *
 * <p>No approval is asked: a re-test runs again what already ran, under the same contract.
 */
@Service
@RequiredArgsConstructor
public class FindingRetestService {

  private final FindingRepository findingRepository;
  private final InjectService injectService;
  private final PermissionService permissionService;
  private final UserService userService;

  /** One queued re-test: the finding and the inject copy that re-tests it. */
  public record FindingRetest(String findingId, String retestInjectId) {}

  /**
   * A detection by {@code injectId}: the finding is open again and this inject set it.
   *
   * <p>Static so the recording path, which writes the row, sets it in its own transaction.
   */
  public static void markDetected(@NotNull final Finding finding, final String injectId) {
    finding.setStatus(FindingStatus.OPEN);
    finding.setStatusInjectId(injectId);
  }

  /**
   * Queues the re-test of the given findings of the tenant: one copy per inject that produced them,
   * however many of its findings are asked.
   *
   * @throws ElementNotFoundException when a finding is not in the tenant, or has no inject left
   * @throws BadRequestException when an asset of a finding is no longer a target of its inject
   * @throws ForbiddenException when the caller may not launch an inject to re-test
   */
  public List<FindingRetest> retest(
      @NotNull final TxCtx ctx,
      @NotNull final String tenantId,
      @NotNull final List<String> findingIds) {
    Set<String> ids = new LinkedHashSet<>(findingIds);
    if (ids.isEmpty()) {
      throw new BadRequestException("At least one finding is required");
    }
    List<Finding> findings = findingRepository.findAllByIdInAndTenantId(ids, tenantId);
    if (findings.size() != ids.size()) {
      Set<String> found = findings.stream().map(Finding::getId).collect(Collectors.toSet());
      ids.removeAll(found);
      throw new ElementNotFoundException("Finding not found: " + String.join(", ", ids));
    }

    // Group by the inject that produced each finding: one copy per inject
    Map<String, Inject> origins = new LinkedHashMap<>();
    Map<String, List<Finding>> byInject = new LinkedHashMap<>();
    for (Finding finding : findings) {
      Inject origin =
          sourceInject(finding)
              .orElseThrow(
                  () ->
                      new ElementNotFoundException(
                          "No inject left to re-test finding " + finding.getId()));
      origins.putIfAbsent(origin.getId(), origin);
      byInject.computeIfAbsent(origin.getId(), id -> new ArrayList<>()).add(finding);
    }

    User user = userService.currentUser();
    List<FindingRetest> retests = new ArrayList<>();
    byInject.forEach(
        (originId, ofInject) -> {
          Inject origin = origins.get(originId);
          if (!permissionService.hasPermissionInTenant(
              ctx, user, origin.getId(), ResourceType.INJECT, Action.LAUNCH)) {
            throw new ForbiddenException("Not allowed to launch inject " + origin.getId());
          }
          throwIfAssetLeftScope(origin, ofInject);
          Inject copy = injectService.duplicateAndQueue(origin.getId());
          for (Finding finding : ofInject) {
            finding.setRetestInjectId(copy.getId());
            retests.add(new FindingRetest(finding.getId(), copy.getId()));
          }
          findingRepository.saveAll(ofInject);
        });
    return retests;
  }

  /** The inject of the latest occurrence of the finding, the one a re-test copies. */
  static Optional<Inject> sourceInject(Finding finding) {
    return finding.getOccurrences().stream()
        .filter(occurrence -> occurrence.getInject() != null)
        .max(Comparator.comparing(FindingOccurrence::getDate))
        .map(FindingOccurrence::getInject);
  }

  /** Every asset of the findings must still be a target of the inject, directly or by a group. */
  private void throwIfAssetLeftScope(Inject origin, List<Finding> findings) {
    Set<String> targeted =
        injectService.resolveAllAssetsToExecute(origin).stream()
            .map(target -> target.asset().getId())
            .collect(Collectors.toSet());
    for (Finding finding : findings) {
      List<String> outside =
          finding.getAssets().stream()
              .map(Asset::getId)
              .filter(assetId -> !targeted.contains(assetId))
              .toList();
      if (!outside.isEmpty()) {
        throw new BadRequestException(
            "Finding "
                + finding.getId()
                + " cannot be re-tested: asset(s) "
                + String.join(", ", outside)
                + " are no longer targeted by inject "
                + origin.getId());
      }
    }
  }
}
