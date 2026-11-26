package io.openaev.rest.finding;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.TypeValueKey;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.specification.FindingSpecification;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.utils.mapper.FindingMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private final FindingRepository findingRepository;
  private final FindingMapper findingMapper;

  public Page<AggregatedFindingOutput> searchDistinctFindings(
      SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (specification, pageable) ->
                findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(specification), pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(Specification.unrestricted(), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByInject(
      String injectId, SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.findFindingsForInject(injectId).and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForInject(injectId), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsBySimulation(
      String simulationId, SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.findFindingsForSimulation(simulationId)
                            .and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForSimulation(simulationId), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByScenario(
      String scenarioId, SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.findFindingsForScenario(scenarioId)
                            .and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForScenario(scenarioId), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByEndpoint(
      String endpointId, SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.findFindingsForEndpoint(endpointId)
                            .and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForEndpoint(endpointId), page);
  }

  public Page<AggregatedFindingOutput> searchDistinctBySpecification(
      Specification<Finding> baseFilterSpec, Page<Finding> page) {

    // Step 1: Extract distinct (type, displayed value) keys. Grouping on the displayed value, never
    // on the cleartext, keeps two secrets that look the same in one row (ADR-017).
    List<TypeValueKey> displayValueKeys =
        page.getContent().stream()
            .map(f -> new TypeValueKey(f.getType(), f.getValueDisplay()))
            .distinct()
            .toList();

    if (displayValueKeys.isEmpty()) {
      return Page.empty(page.getPageable());
    }

    // Step 2: Fetch all findings with assets for those values/types
    List<ContractOutputType> types = displayValueKeys.stream().map(TypeValueKey::getType).toList();
    List<String> displayValues = displayValueKeys.stream().map(TypeValueKey::getValue).toList();

    List<Finding> findingsWithAssets =
        findingRepository.findAll(
            FindingSpecification.findAllWithAssetsByTypeDisplayValueIn(
                types, displayValues, baseFilterSpec));

    // Step 3: Group assets by (type, value)
    Map<TypeValueKey, List<Asset>> groupedAssets =
        findingsWithAssets.stream()
            .filter(
                f -> displayValueKeys.contains(new TypeValueKey(f.getType(), f.getValueDisplay())))
            .flatMap(
                finding ->
                    finding.getAssets().stream()
                        .map(
                            asset ->
                                Map.entry(
                                    new TypeValueKey(finding.getType(), finding.getValueDisplay()),
                                    asset)))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

    // Step 3b: Group-wide row times and detection times per (type, value), across all occurrences
    // in scope - findingsWithAssets holds exactly that set. The page row is the most recent
    // occurrence per group (see FindingSpecification.distinctTypeValueWithFilter), but its own
    // dates are that single occurrence's, not the group's.
    // - created/updated: MIN creation date and MAX update date (row times, any write moves them);
    // - first/last seen: MIN(COALESCE(firstSeen, creationDate)) and MAX(COALESCE(lastSeen,
    //   updateDate)), the rule FindingRepository.findSeenAggregate applies to the summary, so the
    //   summary, the get and the distinct search agree on when a finding was detected (#9846).
    Map<TypeValueKey, Instant> createdByKey = new HashMap<>();
    Map<TypeValueKey, Instant> updatedByKey = new HashMap<>();
    Map<TypeValueKey, Instant> firstSeenByKey = new HashMap<>();
    Map<TypeValueKey, Instant> lastSeenByKey = new HashMap<>();
    for (Finding occurrence : findingsWithAssets) {
      TypeValueKey key = new TypeValueKey(occurrence.getType(), occurrence.getValueDisplay());
      if (!displayValueKeys.contains(key)) {
        continue;
      }
      createdByKey.merge(key, occurrence.getCreationDate(), FindingDistinctSearchService::earliest);
      updatedByKey.merge(key, occurrence.getUpdateDate(), FindingDistinctSearchService::latest);
      firstSeenByKey.merge(
          key, FindingMapper.firstSeenOf(occurrence), FindingDistinctSearchService::earliest);
      lastSeenByKey.merge(
          key, FindingMapper.lastSeenOf(occurrence), FindingDistinctSearchService::latest);
    }

    // Step 4: Map page findings + grouped assets to AggregatedFindingOutput
    return page.map(
        finding -> {
          TypeValueKey key = new TypeValueKey(finding.getType(), finding.getValueDisplay());
          List<Asset> relatedAssets = groupedAssets.getOrDefault(key, List.of());
          return findingMapper.toAggregatedFindingOutput(
              finding,
              relatedAssets,
              createdByKey.getOrDefault(key, finding.getCreationDate()),
              updatedByKey.getOrDefault(key, finding.getUpdateDate()),
              firstSeenByKey.getOrDefault(key, FindingMapper.firstSeenOf(finding)),
              lastSeenByKey.getOrDefault(key, FindingMapper.lastSeenOf(finding)));
        });
  }

  private static Instant earliest(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }

  private static Instant latest(Instant a, Instant b) {
    return a.isAfter(b) ? a : b;
  }
}
