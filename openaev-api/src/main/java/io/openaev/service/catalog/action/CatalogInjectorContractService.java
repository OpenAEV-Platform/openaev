package io.openaev.service.catalog.action;

import static io.openaev.database.specification.CatalogConnectorInjectorContractSpecification.ofInjectorsNotRegisteredIn;
import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.pagination.SearchPaginationInputMapper.translateFields;
import static io.openaev.utils.pagination.SearchPaginationInputMapper.withoutFilter;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.Filters;
import io.openaev.database.repository.CatalogConnectorInjectorContractRepository;
import io.openaev.service.catalog.CatalogFacet;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogInjectorContractService {

  private static final Sort BY_INTEGRATION = Sort.by("catalogConnector.title", "externalId");

  private final CatalogConnectorInjectorContractRepository
      catalogConnectorInjectorContractRepository;

  /** A contract column an Available tab facet reads. */
  private record Column(
      String field, Function<CatalogConnectorInjectorContract, List<String>> values) {}

  private static final Map<String, Column> COLUMNS =
      Map.of(
          CatalogFacet.DOMAIN_KEY,
          new Column(
              "catalog_connector_injector_contract_domains",
              CatalogConnectorInjectorContract::getDomains),
          CatalogFacet.PLATFORMS_KEY,
          new Column(
              "catalog_connector_injector_contract_platforms",
              CatalogConnectorInjectorContract::getPlatforms));

  private static final Map<String, String> FIELDS =
      COLUMNS.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().field()));

  // -- LIST --

  /**
   * The contracts of the catalogue injectors the tenant does not run, under the filters and search
   * of the Available tab, by integration. They come from the integrations list: a few dozen rows.
   */
  @Transactional(readOnly = true)
  public List<CatalogConnectorInjectorContract> notRegistered(
      String tenantId, SearchPaginationInput input) {
    return matching(tenantId, input);
  }

  /**
   * The number of listed contracts per value of each facet, under the other filters; a facet no
   * contract column holds counts none.
   */
  @Transactional(readOnly = true)
  public Map<String, Map<String, Long>> notRegisteredFacetCounts(
      String tenantId, SearchPaginationInput input, List<CatalogFacet> facets) {
    Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
    for (CatalogFacet facet : facets) {
      Column column = COLUMNS.get(facet.key());
      if (column != null) {
        counts.put(
            facet.key(),
            matching(tenantId, withoutFilter(input, facet.key())).stream()
                .flatMap(contract -> column.values().apply(contract).stream())
                .collect(groupingBy(Function.identity(), LinkedHashMap::new, counting())));
      }
    }
    return counts;
  }

  // -- STEPS --

  private List<CatalogConnectorInjectorContract> matching(
      String tenantId, SearchPaginationInput input) {
    if (!answers(input)) {
      return List.of();
    }
    SearchPaginationInput translated = translateFields(input, FIELDS);
    Specification<CatalogConnectorInjectorContract> filters =
        computeFilterGroupJpa(translated.getFilterGroup());
    return catalogConnectorInjectorContractRepository.findAll(
        ofInjectorsNotRegisteredIn(tenantId)
            .and(filters)
            .and(computeSearchJpa(translated.getTextSearch())),
        BY_INTEGRATION);
  }

  // A filter a contract has no column for matches none of them.
  private static boolean answers(SearchPaginationInput input) {
    return Optional.ofNullable(input.getFilterGroup())
        .map(Filters.FilterGroup::getFilters)
        .orElse(List.of())
        .stream()
        .allMatch(filter -> COLUMNS.containsKey(filter.getKey()));
  }
}
