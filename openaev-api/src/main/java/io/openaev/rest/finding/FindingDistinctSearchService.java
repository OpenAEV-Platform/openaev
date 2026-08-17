package io.openaev.rest.finding;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Filters.Filter;
import io.openaev.database.model.Filters.FilterGroup;
import io.openaev.database.model.Filters.FilterMode;
import io.openaev.database.model.Filters.FilterOperator;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingTriage;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.database.model.TypeValueKey;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.FindingTriageRepository;
import io.openaev.database.specification.FindingSpecification;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.mapper.FindingMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FindingDistinctSearchService {

  private static final String TRIAGE_STATUS_FILTER_KEY = "finding_triage_status";
  // Not a persisted column: "archived" is a computed status (manual finding_archived_at OR no
  // re-detection for more than the tenant's configured archiveDays, see
  // FindingSpecification#withArchived), so this filter is extracted and applied the same way the
  // finding_triage_status one is above. Only sent by the main Finding page's Active/Archived tabs
  // (see FindingList.tsx); every scoped caller (by inject/simulation/scenario/endpoint) never sets
  // it and keeps its previous, unfiltered-by-archive-status behavior.
  private static final String ARCHIVED_FILTER_KEY = "finding_archived";

  private final FindingRepository findingRepository;
  private final FindingTriageRepository findingTriageRepository;
  private final FindingMapper findingMapper;
  private final TenantSettingsService tenantSettingsService;

  public Page<AggregatedFindingOutput> searchDistinctFindings(
      SearchPaginationInput searchPaginationInput) {
    Specification<Finding> triageStatusSpecification =
        extractTriageStatusSpecification(searchPaginationInput);
    Specification<Finding> triageStatusOrderSpecification =
        extractTriageStatusOrderSpecification(searchPaginationInput);
    Specification<Finding> archivedSpecification =
        extractArchivedSpecification(searchPaginationInput);
    Page<Finding> page =
        buildPaginationJPA(
            (specification, pageable) ->
                findingRepository.findAll(
                    withTriageStatusOrder(
                        FindingSpecification.distinctTypeValueWithFilter(
                            withArchived(
                                withTriageStatus(specification, triageStatusSpecification),
                                archivedSpecification)),
                        triageStatusOrderSpecification),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(Specification.unrestricted(), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByInject(
      String injectId, SearchPaginationInput searchPaginationInput) {
    Specification<Finding> triageStatusSpecification =
        extractTriageStatusSpecification(searchPaginationInput);
    Specification<Finding> triageStatusOrderSpecification =
        extractTriageStatusOrderSpecification(searchPaginationInput);
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    withTriageStatusOrder(
                        FindingSpecification.distinctTypeValueWithFilter(
                            withTriageStatus(
                                FindingSpecification.findFindingsForInject(injectId)
                                    .and(specification),
                                triageStatusSpecification)),
                        triageStatusOrderSpecification),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForInject(injectId), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsBySimulation(
      String simulationId, SearchPaginationInput searchPaginationInput) {
    Specification<Finding> triageStatusSpecification =
        extractTriageStatusSpecification(searchPaginationInput);
    Specification<Finding> triageStatusOrderSpecification =
        extractTriageStatusOrderSpecification(searchPaginationInput);
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    withTriageStatusOrder(
                        FindingSpecification.distinctTypeValueWithFilter(
                            withTriageStatus(
                                FindingSpecification.findFindingsForSimulation(simulationId)
                                    .and(specification),
                                triageStatusSpecification)),
                        triageStatusOrderSpecification),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForSimulation(simulationId), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByScenario(
      String scenarioId, SearchPaginationInput searchPaginationInput) {
    Specification<Finding> triageStatusSpecification =
        extractTriageStatusSpecification(searchPaginationInput);
    Specification<Finding> triageStatusOrderSpecification =
        extractTriageStatusOrderSpecification(searchPaginationInput);
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    withTriageStatusOrder(
                        FindingSpecification.distinctTypeValueWithFilter(
                            withTriageStatus(
                                FindingSpecification.findFindingsForScenario(scenarioId)
                                    .and(specification),
                                triageStatusSpecification)),
                        triageStatusOrderSpecification),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForScenario(scenarioId), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByEndpoint(
      String endpointId, SearchPaginationInput searchPaginationInput) {
    Specification<Finding> triageStatusSpecification =
        extractTriageStatusSpecification(searchPaginationInput);
    Specification<Finding> triageStatusOrderSpecification =
        extractTriageStatusOrderSpecification(searchPaginationInput);
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    withTriageStatusOrder(
                        FindingSpecification.distinctTypeValueWithFilter(
                            withTriageStatus(
                                FindingSpecification.findFindingsForEndpoint(endpointId)
                                    .and(specification),
                                triageStatusSpecification)),
                        triageStatusOrderSpecification),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForEndpoint(endpointId), page);
  }

  private static Specification<Finding> withTriageStatus(
      Specification<Finding> specification, Specification<Finding> triageStatusSpecification) {
    return triageStatusSpecification == null
        ? specification
        : specification.and(triageStatusSpecification);
  }

  private static Specification<Finding> withArchived(
      Specification<Finding> specification, Specification<Finding> archivedSpecification) {
    return archivedSpecification == null ? specification : specification.and(archivedSpecification);
  }

  /**
   * Combines the given (already distinct-wrapped) specification with the custom {@code
   * finding_triage_status} ORDER BY built by {@link
   * #extractTriageStatusOrderSpecification(SearchPaginationInput)}, applied against the OUTER
   * query's root - never against {@link
   * io.openaev.database.specification.FindingSpecification#distinctTypeValueWithFilter}'s internal
   * subquery root, which would reference a join alias absent from the outer query's FROM clause.
   */
  private static Specification<Finding> withTriageStatusOrder(
      Specification<Finding> distinctSpecification,
      Specification<Finding> triageStatusOrderSpecification) {
    return triageStatusOrderSpecification == null
        ? distinctSpecification
        : distinctSpecification.and(triageStatusOrderSpecification);
  }

  /**
   * Extracts and removes any {@code finding_triage_status} sort request from the input's sort list,
   * returning an equivalent Specification that sets a custom {@code ORDER BY} on the query instead
   * of relying on the generic {@code SortUtilsJpa}/{@code @Queryable(sortable = true)} mechanism.
   *
   * <p>{@link Finding#triage} is the inverse (mappedBy) side of a 1:1 relation with no physical
   * column on the {@code findings} table, so it cannot be sorted via a plain JPA property path like
   * a regular column or a ManyToOne association - Hibernate has nothing to order by without an
   * explicit join. It also needs the same NULL-as-virtual-UNTRIAGED handling as the filter above: a
   * finding with no {@link FindingTriage} row must sort alongside real UNTRIAGED rows, not be
   * pushed to one end by SQL's NULL-ordering rules. Both are achieved with a CASE expression
   * ranking the (possibly absent) status, removing the sort from the generic list so {@code
   * SortUtilsJpa} never sees - and never rejects - this non-plain-column property.
   */
  private Specification<Finding> extractTriageStatusOrderSpecification(
      SearchPaginationInput searchPaginationInput) {
    List<SortField> sorts = searchPaginationInput.getSorts();
    if (sorts == null) {
      return null;
    }

    Optional<SortField> sortField =
        sorts.stream().filter(s -> TRIAGE_STATUS_FILTER_KEY.equals(s.property())).findFirst();
    if (sortField.isEmpty()) {
      return null;
    }
    // Remove it from the list so the generic SortUtilsJpa mechanism never sees it - it would
    // otherwise throw InvalidSortPropertyException since this field has no @Queryable(sortable =
    // true) (see Finding#triage javadoc for why it cannot be marked sortable directly).
    sorts.removeIf(s -> TRIAGE_STATUS_FILTER_KEY.equals(s.property()));

    boolean descending = "desc".equalsIgnoreCase(sortField.get().direction());

    return (root, query, cb) -> {
      // The outer query built by FindingSpecification#distinctTypeValueWithFilter sets
      // query.distinct(true), but its rows are already unique (its WHERE clause is a plain
      // `root.id IN (subquery of one min-id per type/value group)`, so no join on this outer root
      // can multiply rows). Postgres rejects `SELECT DISTINCT ... ORDER BY <expr not in SELECT
      // list>`, which the ranking expression below would otherwise violate since it comes from a
      // joined entity's column, not one of the root's own selected columns. Turning distinct off
      // here is safe precisely because it is redundant on this call path, and does not affect any
      // other query built from FindingSpecification#distinctTypeValueWithFilter elsewhere, since
      // Criteria mutations are local to this query instance.
      query.distinct(false);
      Join<Finding, FindingTriage> triageJoin = root.join("triage", JoinType.LEFT);
      // Rank mirrors FindingTriageStatus's declared (and native-Postgres-enum) order: a missing
      // row and a real UNTRIAGED row both rank first, then CONFIRMED, FALSE_POSITIVE,
      // RISK_ACCEPTED.
      Expression<Integer> rank =
          cb.<Integer>selectCase()
              .when(cb.isNull(triageJoin.get("id")), 0)
              .when(cb.equal(triageJoin.get("status"), FindingTriageStatus.UNTRIAGED), 0)
              .when(cb.equal(triageJoin.get("status"), FindingTriageStatus.CONFIRMED), 1)
              .when(cb.equal(triageJoin.get("status"), FindingTriageStatus.FALSE_POSITIVE), 2)
              .otherwise(3);
      // Set the ORDER BY directly on the query rather than returning it via Sort: this
      // specification is combined into the outer query built by buildPaginationJPA, whose Pageable
      // now carries an empty Sort (the field was removed above), so Spring Data JPA never calls
      // query.orderBy() itself and overwrites this one.
      query.orderBy(descending ? cb.desc(rank) : cb.asc(rank));
      return cb.conjunction();
    };
  }

  /**
   * Extracts and removes any {@code finding_triage_status} filter from the input's filter group,
   * returning an equivalent Specification built outside the generic {@code FilterUtilsJpa}
   * mechanism.
   *
   * <p>This field is exposed via {@link Finding#triage}, a 1:1 relation to {@link FindingTriage}
   * that may have no row at all for a finding that has never been triaged (a missing row means a
   * *virtual* {@link FindingTriageStatus#UNTRIAGED}, never persisted - see {@code
   * FindingTriageService#getCurrentStatus} javadoc). The generic filter engine would build a plain
   * {@code LEFT JOIN ... WHERE triage.status = 'UNTRIAGED'}, which silently excludes every finding
   * with no row since a LEFT JOIN's unmatched side is SQL NULL, and {@code NULL = 'UNTRIAGED'} is
   * never true. This method special-cases the UNTRIAGED value to also match a NULL join, and is
   * applied outside the filter group precisely so the shared FilterUtilsJpa mechanism (used by
   * every other entity/field) does not need this bespoke NULL-handling.
   */
  private Specification<Finding> extractTriageStatusSpecification(
      SearchPaginationInput searchPaginationInput) {
    FilterGroup filterGroup = searchPaginationInput.getFilterGroup();
    if (filterGroup == null || filterGroup.getFilters() == null) {
      return null;
    }

    Optional<Filter> triageStatusFilter = filterGroup.findByKey(TRIAGE_STATUS_FILTER_KEY);
    if (triageStatusFilter.isEmpty()) {
      return null;
    }
    // Remove it from the group so the generic FilterUtilsJpa mechanism never sees it.
    filterGroup.removeByKey(TRIAGE_STATUS_FILTER_KEY);

    Filter filter = triageStatusFilter.get();
    List<FindingTriageStatus> statuses =
        Optional.ofNullable(filter.getValues()).orElse(List.of()).stream()
            .map(FindingTriageStatus::valueOf)
            .toList();
    if (statuses.isEmpty()) {
      return null;
    }
    boolean negate = FilterOperator.not_eq.equals(filter.getOperator());
    FilterMode mode = Optional.ofNullable(filter.getMode()).orElse(FilterMode.or);

    return (root, query, cb) -> {
      Join<Finding, FindingTriage> triageJoin = root.join("triage", JoinType.LEFT);
      // Each membership predicate below is deliberately built to never evaluate to SQL NULL (only
      // TRUE/FALSE), by explicitly testing triageJoin.id IS [NOT] NULL rather than relying on a
      // bare `status = ?` equality - which is NULL (neither true nor false) for a finding with no
      // FindingTriage row. This matters for the `not_eq` operator below: negating a predicate that
      // can be NULL (`cb.not(NULL)` is still NULL, not TRUE) would incorrectly drop every
      // no-row/virtually-UNTRIAGED finding from a "not_eq CONFIRMED" style filter.
      Predicate[] predicates =
          statuses.stream()
              .map(
                  status ->
                      FindingTriageStatus.UNTRIAGED.equals(status)
                          ? cb.or(
                              cb.isNull(triageJoin.get("id")),
                              cb.equal(triageJoin.get("status"), status))
                          : cb.and(
                              cb.isNotNull(triageJoin.get("id")),
                              cb.equal(triageJoin.get("status"), status)))
              .toArray(Predicate[]::new);
      Predicate combined = FilterMode.and.equals(mode) ? cb.and(predicates) : cb.or(predicates);
      return negate ? cb.not(combined) : combined;
    };
  }

  /**
   * Extracts and removes any {@code finding_archived} filter from the input's filter group,
   * returning an equivalent Specification built outside the generic {@code FilterUtilsJpa}
   * mechanism, mirroring {@link #extractTriageStatusSpecification} above.
   *
   * <p>"Archived" is not a persisted boolean column: a finding is archived either because it was
   * manually archived ({@code finding_archived_at} set, see {@link FindingArchiveService}) or
   * because it has not been re-detected for more than the tenant's configured archiveDays (see
   * {@code TenantSettingsService#findFindingArchiveDays}). {@link
   * FindingSpecification#withArchived} encodes both conditions directly in SQL so archived findings
   * can be excluded (or included, for the "Archived" tab) at the database level instead of being
   * fetched and merely hidden client-side.
   */
  private Specification<Finding> extractArchivedSpecification(
      SearchPaginationInput searchPaginationInput) {
    FilterGroup filterGroup = searchPaginationInput.getFilterGroup();
    if (filterGroup == null || filterGroup.getFilters() == null) {
      return null;
    }

    Optional<Filter> archivedFilter = filterGroup.findByKey(ARCHIVED_FILTER_KEY);
    if (archivedFilter.isEmpty()) {
      return null;
    }
    // Remove it from the group so the generic FilterUtilsJpa mechanism never sees it - there is
    // no such physical column to filter on.
    filterGroup.removeByKey(ARCHIVED_FILTER_KEY);

    List<String> values = Optional.ofNullable(archivedFilter.get().getValues()).orElse(List.of());
    if (values.isEmpty()) {
      return null;
    }
    boolean archived = Boolean.parseBoolean(values.get(0));
    int archiveDays =
        tenantSettingsService.findFindingArchiveDays(TenantContext.getCurrentTenant());
    return FindingSpecification.withArchived(archived, archiveDays);
  }

  public Page<AggregatedFindingOutput> searchDistinctBySpecification(
      Specification<Finding> baseFilterSpec, Page<Finding> page) {

    // Step 1: Extract distinct (type, value) keys
    List<TypeValueKey> typeValueKeys =
        page.getContent().stream()
            .map(f -> new TypeValueKey(f.getType(), f.getValue()))
            .distinct()
            .toList();

    if (typeValueKeys.isEmpty()) {
      return Page.empty(page.getPageable());
    }

    // Step 2: Fetch all findings with assets for those values/types
    List<ContractOutputType> types = typeValueKeys.stream().map(TypeValueKey::getType).toList();
    List<String> values = typeValueKeys.stream().map(TypeValueKey::getValue).toList();

    List<Finding> findingsWithAssets =
        findingRepository.findAll(
            FindingSpecification.findAllWithAssetsByTypeValueIn(types, values, baseFilterSpec));

    // Step 3: Group assets by (type, value)
    Map<TypeValueKey, List<Asset>> groupedAssets =
        findingsWithAssets.stream()
            .filter(f -> typeValueKeys.contains(new TypeValueKey(f.getType(), f.getValue())))
            .flatMap(
                finding ->
                    finding.getAssets().stream()
                        .map(
                            asset ->
                                Map.entry(
                                    new TypeValueKey(finding.getType(), finding.getValue()),
                                    asset)))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    // Step 4: Bulk-fetch triage statuses for the page's findings (one query total, not one per
    // finding) to avoid N+1 - see FindingTriageRepository#findByFinding_IdIn.
    List<String> findingIds = page.getContent().stream().map(Finding::getId).toList();
    Map<String, FindingTriageStatus> triageStatusByFindingId =
        findingTriageRepository.findByFinding_IdIn(findingIds).stream()
            .collect(
                Collectors.toMap(triage -> triage.getFinding().getId(), FindingTriage::getStatus));

    // Step 5: Map page findings + grouped assets + triage statuses to AggregatedFindingOutput
    return page.map(
        finding -> {
          TypeValueKey key = new TypeValueKey(finding.getType(), finding.getValue());
          List<Asset> relatedAssets = groupedAssets.getOrDefault(key, List.of());
          return findingMapper.toAggregatedFindingOutput(
              finding, relatedAssets, triageStatusByFindingId);
        });
  }
}
