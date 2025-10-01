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
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.forLatestSimulations().and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(FindingSpecification.forLatestSimulations(), page);
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
                            .and(FindingSpecification.forLatestSimulations())
                            .and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForScenario(scenarioId)
            .and(FindingSpecification.forLatestSimulations()),
        page);
  }

  public Page<AggregatedFindingOutput> searchDistinctFindingsByEndpoint(
      String endpointId, SearchPaginationInput searchPaginationInput) {
    Page<Finding> page =
        buildPaginationJPA(
            (Specification<Finding> specification, Pageable pageable) ->
                this.findingRepository.findAll(
                    FindingSpecification.distinctTypeValueWithFilter(
                        FindingSpecification.findFindingsForEndpoint(endpointId)
                            .and(FindingSpecification.forLatestSimulations())
                            .and(specification)),
                    pageable),
            searchPaginationInput,
            Finding.class);

    return searchDistinctBySpecification(
        FindingSpecification.findFindingsForEndpoint(endpointId)
            .and(FindingSpecification.forLatestSimulations()),
        page);
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

    // Step 4: Map page findings + grouped assets to AggregatedFindingOutput
    return page.map(
        finding -> {
          TypeValueKey key = new TypeValueKey(finding.getType(), finding.getValue());
          List<Asset> relatedAssets = groupedAssets.getOrDefault(key, List.of());
          return findingMapper.toAggregatedFindingOutput(finding, relatedAssets);
        });
  }
}
