package io.openaev.service.finding;

import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.TxCtxScopeUtils.tenantIdsFromHTTPCtx;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SearchPaginationInputMapper.translateFields;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;

import io.openaev.api.finding.FindingLocationOutput;
import io.openaev.api.finding.FindingOccurrenceOutput;
import io.openaev.api.finding.StableFindingFacetCountsOutput;
import io.openaev.api.finding.StableFindingFacetCountsOutput.AssetFacetOutput;
import io.openaev.api.finding.StableFindingFacetCountsOutput.SourceFacetOutput;
import io.openaev.api.finding.StableFindingMapper;
import io.openaev.api.finding.StableFindingOutput;
import io.openaev.api.finding.StableFindingSummaryOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Filters.Filter;
import io.openaev.database.model.Filters.FilterGroup;
import io.openaev.database.model.Filters.FilterMode;
import io.openaev.database.model.Filters.FilterOperator;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.Injector;
import io.openaev.database.model.StableFinding;
import io.openaev.database.repository.FindingOccurrenceRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.StableFindingRepository;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
  private static final String SEVERITY_FILTER = "finding_severity";
  private static final String TYPE_FILTER = "finding_type";
  private static final String PROVIDER_FILTER = "finding_cloud_provider";
  private static final String TRIAGE_FILTER = "finding_triage_status";
  private static final String SOURCE_FILTER = "finding_source";
  private static final String ASSET_FILTER = "finding_assets";
  private static final String ASSET_GROUP_FILTER = "finding_asset_groups";
  private static final Set<String> OCCURRENCE_FILTERS = Set.of(ASSET_GROUP_FILTER, SOURCE_FILTER);
  private static final String ASSET_SORT = "finding_asset_name";
  private static final String ASSET_MISSING_SORT = "finding_asset_missing";
  private static final String ID_SORT = "finding_id";
  private static final int MAX_SIBLINGS_PAGE = 100;

  private final StableFindingRepository stableFindingRepository;
  private final FindingOccurrenceRepository findingOccurrenceRepository;
  private final StableFindingMapper stableFindingMapper;
  private final InjectorRepository injectorRepository;

  /**
   * Searches stable identities in the explicitly authorized tenant scope and bulk-loads their
   * occurrences for constant-query mapping. Asset group, Source and the text search look into the
   * occurrences through EXISTS subqueries, so a finding is never listed twice.
   */
  @Transactional(readOnly = true)
  public Page<StableFindingOutput> search(TxCtx ctx, SearchPaginationInput input) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    Specification<StableFinding> scope =
        tenantScope(tenantIds).and(occurrenceFilters(input.getFilterGroup(), null));
    SearchPaginationInput jpaInput = jpaInput(input, null);
    Page<StableFinding> page =
        buildPaginationJPA(
            (specification, pageable) ->
                stableFindingRepository.findAll(scope.and(specification), pageable),
            jpaInput,
            StableFinding.class,
            searchSpecification(input.getTextSearch()));
    Map<String, List<FindingOccurrence>> occurrencesByFinding =
        occurrencesByFinding(page.getContent(), tenantIds);
    Map<String, Injector> injectors =
        injectorsById(occurrencesByFinding.values().stream().flatMap(List::stream).toList());
    return page.map(
        finding ->
            stableFindingMapper.toOutput(
                finding, occurrencesByFinding.getOrDefault(finding.getId(), List.of()), injectors));
  }

  /**
   * Returns full-result facet counts for the quick filters (Severity, Type, Asset). Each facet
   * ignores its own selected values while preserving search text and every other filter, so users
   * can add alternatives without a selected row collapsing all sibling counts to zero.
   */
  @Transactional(readOnly = true)
  public StableFindingFacetCountsOutput facetCounts(TxCtx ctx, SearchPaginationInput input) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    return new StableFindingFacetCountsOutput(
        stableFindingRepository.countBySeverity(
            facetSpecification(input, tenantIds, SEVERITY_FILTER)),
        stableFindingRepository.countByType(facetSpecification(input, tenantIds, TYPE_FILTER)),
        stableFindingRepository.countByCloudProvider(
            facetSpecification(input, tenantIds, PROVIDER_FILTER)),
        stableFindingRepository.countByTriageStatus(
            facetSpecification(input, tenantIds, TRIAGE_FILTER)),
        stableFindingRepository
            .countBySource(facetSpecification(input, tenantIds, SOURCE_FILTER))
            .values()
            .stream()
            .map(
                source ->
                    new SourceFacetOutput(
                        source.id(), source.name(), source.type(), source.count()))
            .toList(),
        stableFindingRepository
            .countByAsset(facetSpecification(input, tenantIds, ASSET_FILTER))
            .values()
            .stream()
            .map(asset -> new AssetFacetOutput(asset.id(), asset.name(), asset.count()))
            .toList());
  }

  /**
   * "Also detected on" (US.4): the other findings of the tenant with the same type and value, each
   * at its own location, most recently seen first.
   */
  @Transactional(readOnly = true)
  public Page<StableFindingOutput> siblings(TxCtx ctx, String id, int page, int size) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    StableFinding finding = requireFinding(id, tenantIds);
    Page<StableFinding> siblings =
        stableFindingRepository.findSiblings(
            Set.of(finding.getTenant().getId()),
            finding.getType(),
            finding.getValue(),
            finding.getId(),
            PageRequest.of(page, Math.min(Math.max(size, 1), MAX_SIBLINGS_PAGE)));
    Map<String, List<FindingOccurrence>> occurrencesByFinding =
        occurrencesByFinding(siblings.getContent(), tenantIds);
    return siblings.map(
        sibling ->
            stableFindingMapper.toOutput(
                sibling, occurrencesByFinding.getOrDefault(sibling.getId(), List.of())));
  }

  /** Returns one stable identity only when its id belongs to an authorized tenant. */
  @Transactional(readOnly = true)
  public StableFindingOutput findById(TxCtx ctx, String id) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    StableFinding finding = requireFinding(id, tenantIds);
    List<FindingOccurrence> occurrences = findOccurrences(id, tenantIds);
    return stableFindingMapper.toOutput(finding, occurrences, injectorsById(occurrences));
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

  private Specification<StableFinding> facetSpecification(
      SearchPaginationInput input, Set<String> tenantIds, String excludedFilter) {
    return tenantScope(tenantIds)
        .and(computeFilterGroupJpa(jpaInput(input, excludedFilter).getFilterGroup()))
        .and(occurrenceFilters(input.getFilterGroup(), excludedFilter))
        .and(searchSpecification(input.getTextSearch()));
  }

  private static Specification<StableFinding> tenantScope(Set<String> tenantIds) {
    return (root, query, cb) -> root.get("tenant").get("id").in(tenantIds);
  }

  /**
   * Copy of the input for the generic JPA filters and sort: the filters on occurrences (Asset
   * group, Source) are removed, they are applied by {@link #occurrenceFilters}; findings without
   * asset stay last when sorting by asset, and the finding id breaks ties (stable order).
   */
  private static SearchPaginationInput jpaInput(
      SearchPaginationInput input, String excludedFilter) {
    SearchPaginationInput copy = new SearchPaginationInput();
    copy.setPage(input.getPage());
    copy.setSize(input.getSize());
    copy.setTextSearch(null);
    FilterGroup source = input.getFilterGroup();
    if (source != null) {
      copy.setFilterGroup(
          FilterGroup.builder()
              .mode(source.getMode())
              .filters(
                  source.getFilters() == null
                      ? List.of()
                      : source.getFilters().stream()
                          .filter(filter -> !filter.getKey().equals(excludedFilter))
                          .filter(filter -> !OCCURRENCE_FILTERS.contains(filter.getKey()))
                          .toList())
              .build());
    }
    List<SortField> sorts = new ArrayList<>();
    for (SortField sort : input.getSorts() == null ? List.<SortField>of() : input.getSorts()) {
      if (ASSET_SORT.equals(sort.property())) {
        sorts.add(new SortField(ASSET_MISSING_SORT, "ASC", null));
      }
      sorts.add(sort);
    }
    if (sorts.stream().noneMatch(sort -> ID_SORT.equals(sort.property()))) {
      sorts.add(new SortField(ID_SORT, "ASC", null));
    }
    copy.setSorts(sorts);
    return copy;
  }

  /** Asset group (groups targeted by the detecting inject, today's behaviour) and Source. */
  private static Specification<StableFinding> occurrenceFilters(
      FilterGroup group, String excludedFilter) {
    if (group == null || group.getFilters() == null) {
      return Specification.unrestricted();
    }
    Specification<StableFinding> result = Specification.unrestricted();
    for (Filter filter : group.getFilters()) {
      if (filter.getKey().equals(excludedFilter)
          || !OCCURRENCE_FILTERS.contains(filter.getKey())
          || filter.getValues() == null
          || filter.getValues().isEmpty()) {
        continue;
      }
      Specification<StableFinding> exists =
          (root, query, cb) -> {
            Subquery<String> sub = query.subquery(String.class);
            Root<FindingOccurrence> occurrence = sub.from(FindingOccurrence.class);
            Predicate match =
                ASSET_GROUP_FILTER.equals(filter.getKey())
                    ? occurrence.join("inject").join("assetGroups").get("id").in(filter.getValues())
                    : occurrence.get("sourceInjectorId").in(filter.getValues());
            sub.select(occurrence.get("id"))
                .where(cb.equal(occurrence.get("stableFinding"), root), match);
            return filter.getOperator() == FilterOperator.not_eq
                ? cb.not(cb.exists(sub))
                : cb.exists(sub);
          };
      result = group.getMode() == FilterMode.or ? result.or(exists) : result.and(exists);
    }
    return result;
  }

  /**
   * Same fields as the current Findings page (US.1): the value, and the inject, simulation and
   * scenario of any detection.
   */
  private static Specification<StableFinding> searchSpecification(String textSearch) {
    if (textSearch == null || textSearch.isBlank()) {
      return Specification.unrestricted();
    }
    Specification<StableFinding> onFinding = computeSearchJpa(textSearch);
    Specification<StableFinding> onOccurrences =
        (root, query, cb) -> {
          String pattern = "%" + textSearch.trim().toLowerCase(Locale.ROOT) + "%";
          Subquery<String> sub = query.subquery(String.class);
          Root<FindingOccurrence> occurrence = sub.from(FindingOccurrence.class);
          Join<Object, Object> inject = occurrence.join("inject", JoinType.LEFT);
          Join<Object, Object> exercise = inject.join("exercise", JoinType.LEFT);
          Join<Object, Object> scenario = exercise.join("scenario", JoinType.LEFT);
          sub.select(occurrence.get("id"))
              .where(
                  cb.equal(occurrence.get("stableFinding"), root),
                  cb.or(
                      cb.like(cb.lower(inject.get("title")), pattern),
                      cb.like(cb.lower(exercise.get("name")), pattern),
                      cb.like(cb.lower(scenario.get("name")), pattern),
                      cb.like(cb.lower(occurrence.get("title")), pattern)));
          return cb.exists(sub);
        };
    return onFinding.or(onOccurrences);
  }

  private Map<String, Injector> injectorsById(Collection<FindingOccurrence> occurrences) {
    List<String> ids =
        occurrences.stream()
            .map(FindingOccurrence::getSourceInjectorId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<String, Injector> byId = new LinkedHashMap<>();
    injectorRepository.findAllByInjectorIdIn(ids).forEach(i -> byId.putIfAbsent(i.getId(), i));
    return byId;
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
