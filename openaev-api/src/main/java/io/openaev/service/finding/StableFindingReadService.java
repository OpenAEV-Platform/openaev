package io.openaev.service.finding;

import static io.openaev.utils.TxCtxScopeUtils.tenantIdsFromHTTPCtx;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SearchPaginationInputMapper.translateFields;

import io.openaev.api.finding.FindingLocationOutput;
import io.openaev.api.finding.FindingOccurrenceOutput;
import io.openaev.api.finding.StableFindingMapper;
import io.openaev.api.finding.StableFindingOutput;
import io.openaev.api.finding.StableFindingSummaryOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.StableFinding;
import io.openaev.database.repository.FindingOccurrenceRepository;
import io.openaev.database.repository.StableFindingRepository;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class StableFindingReadService {

  private static final Map<String, String> OCCURRENCE_FIELDS =
      Map.ofEntries(
          Map.entry("finding_type", "stableFinding.type"),
          Map.entry("finding_value", "stableFinding.value"),
          Map.entry("finding_created_at", "observedAt"),
          Map.entry("finding_updated_at", "observedAt"),
          Map.entry("finding_inject_id", "inject.id"),
          Map.entry("finding_simulation", "inject.exercise.id"),
          Map.entry("finding_scenario", "inject.exercise.scenario.id"),
          Map.entry("finding_assets", "assets.id"),
          Map.entry("finding_teams", "teams.id"),
          Map.entry("finding_users", "users.id"),
          Map.entry("finding_asset_groups", "inject.assetGroups.id"));

  private final StableFindingRepository stableFindingRepository;
  private final FindingOccurrenceRepository findingOccurrenceRepository;
  private final StableFindingMapper stableFindingMapper;

  /**
   * Searches stable identities in the explicitly authorized tenant scope and bulk-loads their
   * occurrences for constant-query mapping.
   */
  @Transactional(readOnly = true)
  public Page<StableFindingOutput> search(TxCtx ctx, SearchPaginationInput input) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    Specification<StableFinding> tenantScope =
        (root, query, cb) -> root.get("tenant").get("id").in(tenantIds);
    Page<StableFinding> page =
        buildPaginationJPA(
            (specification, pageable) ->
                stableFindingRepository.findAll(tenantScope.and(specification), pageable),
            input,
            StableFinding.class);
    Map<String, List<FindingOccurrence>> occurrencesByFinding =
        occurrencesByFinding(page.getContent(), tenantIds);
    return page.map(
        finding ->
            stableFindingMapper.toOutput(
                finding, occurrencesByFinding.getOrDefault(finding.getId(), List.of())));
  }

  /** Returns one stable identity only when its id belongs to an authorized tenant. */
  @Transactional(readOnly = true)
  public StableFindingOutput findById(TxCtx ctx, String id) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    StableFinding finding = requireFinding(id, tenantIds);
    return stableFindingMapper.toOutput(finding, findOccurrences(id, tenantIds));
  }

  /** Returns the first/last-seen and distinct impact counters for a stable identity. */
  @Transactional(readOnly = true)
  public StableFindingSummaryOutput summary(TxCtx ctx, String id) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    StableFinding finding = requireFinding(id, tenantIds);
    return stableFindingMapper.toSummary(finding, findOccurrences(id, tenantIds));
  }

  /**
   * Groups occurrence history by Location without creating additional stable Finding identities.
   */
  @Transactional(readOnly = true)
  public List<FindingLocationOutput> locations(TxCtx ctx, String id) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    requireFinding(id, tenantIds);
    Map<LocationIdentity, List<FindingOccurrence>> grouped =
        findOccurrences(id, tenantIds).stream()
            .collect(
                Collectors.groupingBy(
                    occurrence ->
                        new LocationIdentity(
                            occurrence.getLocationType(),
                            occurrence.getLocationKey(),
                            occurrence.getLocation()),
                    LinkedHashMap::new,
                    Collectors.toList()));
    return grouped.entrySet().stream()
        .map(
            entry -> {
              List<FindingOccurrence> occurrences = entry.getValue();
              FindingOccurrence latest = occurrences.getFirst();
              return FindingLocationOutput.builder()
                  .location(entry.getKey().location())
                  .locationType(entry.getKey().type())
                  .locationKey(entry.getKey().key())
                  .firstSeen(
                      occurrences.stream()
                          .map(FindingOccurrence::getObservedAt)
                          .min(Instant::compareTo)
                          .orElseThrow())
                  .lastSeen(
                      occurrences.stream()
                          .map(FindingOccurrence::getObservedAt)
                          .max(Instant::compareTo)
                          .orElseThrow())
                  .occurrences(occurrences.size())
                  .severity(latest.getObservedSeverity())
                  .build();
            })
        .toList();
  }

  /** Searches point-in-time observations belonging to one authorized stable identity. */
  @Transactional(readOnly = true)
  public Page<FindingOccurrenceOutput> searchOccurrences(
      TxCtx ctx, String stableFindingId, SearchPaginationInput input) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    requireFinding(stableFindingId, tenantIds);
    Specification<FindingOccurrence> scope =
        (root, query, cb) ->
            cb.and(
                cb.equal(root.get("stableFinding").get("id"), stableFindingId),
                root.get("tenant").get("id").in(tenantIds));
    SearchPaginationInput translatedInput = translateFields(input, OCCURRENCE_FIELDS);
    translatedInput.setSorts(input.getSorts());
    if (translatedInput.getFilterGroup() != null
        && translatedInput.getFilterGroup().getFilters() != null) {
      translatedInput
          .getFilterGroup()
          .setFilters(
              translatedInput.getFilterGroup().getFilters().stream()
                  .filter(
                      filter ->
                          !Set.of("stableFinding.type", "stableFinding.value")
                              .contains(filter.getKey()))
                  .toList());
    }
    return buildPaginationJPA(
            (specification, pageable) ->
                findingOccurrenceRepository.findAll(scope.and(specification), pageable),
            translatedInput,
            FindingOccurrence.class)
        .map(stableFindingMapper::toOccurrenceOutput);
  }

  private StableFinding requireFinding(String id, Set<String> tenantIds) {
    return stableFindingRepository
        .findByIdAndTenantIdIn(id, tenantIds)
        .orElseThrow(() -> new EntityNotFoundException("Stable finding not found: " + id));
  }

  private List<FindingOccurrence> findOccurrences(String id, Set<String> tenantIds) {
    return findingOccurrenceRepository
        .findAllByStableFindingIdInAndTenantIdInOrderByObservedAtDescIdDesc(List.of(id), tenantIds);
  }

  private Map<String, List<FindingOccurrence>> occurrencesByFinding(
      Collection<StableFinding> findings, Set<String> tenantIds) {
    if (findings.isEmpty()) {
      return Map.of();
    }
    return findingOccurrenceRepository
        .findAllByStableFindingIdInAndTenantIdInOrderByObservedAtDescIdDesc(
            findings.stream().map(StableFinding::getId).toList(), tenantIds)
        .stream()
        .collect(Collectors.groupingBy(occurrence -> occurrence.getStableFinding().getId()));
  }

  private record LocationIdentity(
      io.openaev.database.model.FindingLocationType type, String key, String location) {}
}
