package io.openaev.rest.finding;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

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
import io.openaev.utils.mapper.FindingMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
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

  private final FindingRepository findingRepository;
  private final FindingTriageRepository findingTriageRepository;
  private final FindingMapper findingMapper;

  public Page<AggregatedFindingOutput> searchDistinctFindings(
      SearchPaginationInput searchPaginationInput) {
    Specification<Finding> triageStatusSpecification =
        extractTriageStatusSpecification(searchPaginationInput);
    Page<Finding> page =
        buildPaginationJPA(
            (specification, pageable) ->
                findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        withTriageStatus(specification, triageStatusSpecification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(Specification.unrestricted(), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByInject(
      String injectId, SearchPaginationInput searchPaginationInput) {
    Specification<Finding> triageStatusSpecification =
        extractTriageStatusSpecification(searchPaginationInput);
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        withTriageStatus(
                            FindingSpecification.findFindingsForInject(injectId)
                                .and(specification),
                            triageStatusSpecification)),
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
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        withTriageStatus(
                            FindingSpecification.findFindingsForSimulation(simulationId)
                                .and(specification),
                            triageStatusSpecification)),
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
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        withTriageStatus(
                            FindingSpecification.findFindingsForScenario(scenarioId)
                                .and(specification),
                            triageStatusSpecification)),
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
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        withTriageStatus(
                            FindingSpecification.findFindingsForEndpoint(endpointId)
                                .and(specification),
                            triageStatusSpecification)),
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
            .collect(Collectors.toMap(triage -> triage.getFinding().getId(), FindingTriage::getStatus));

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
