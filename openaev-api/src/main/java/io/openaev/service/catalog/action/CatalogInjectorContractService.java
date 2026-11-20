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
import java.util.Arrays;
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

  /** The Available tab filters a contract answers, and the contract column each one reads. */
  private enum Facet {
    DOMAIN(
        CatalogFacet.DOMAIN_KEY,
        "catalog_connector_injector_contract_domains",
        CatalogConnectorInjectorContract::getDomains),
    PLATFORMS(
        CatalogFacet.PLATFORMS_KEY,
        "catalog_connector_injector_contract_platforms",
        CatalogConnectorInjectorContract::getPlatforms);

    private static final Map<String, String> FIELDS =
        Arrays.stream(values()).collect(Collectors.toMap(facet -> facet.key, facet -> facet.field));

    private final String key;
    private final String field;
    private final Function<CatalogConnectorInjectorContract, List<String>> values;

    Facet(
        String key, String field, Function<CatalogConnectorInjectorContract, List<String>> values) {
      this.key = key;
      this.field = field;
      this.values = values;
    }

    // A filter a contract has no column for matches none of them.
    static boolean answers(SearchPaginationInput input) {
      return Optional.ofNullable(input.getFilterGroup())
          .map(Filters.FilterGroup::getFilters)
          .orElse(List.of())
          .stream()
          .allMatch(filter -> FIELDS.containsKey(filter.getKey()));
    }
  }

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

  /** The number of listed contracts per value of each facet, under the other filters. */
  @Transactional(readOnly = true)
  public Map<String, Map<String, Long>> notRegisteredFacetCounts(
      String tenantId, SearchPaginationInput input) {
    Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
    for (Facet facet : Facet.values()) {
      counts.put(
          facet.key,
          matching(tenantId, withoutFilter(input, facet.key)).stream()
              .flatMap(contract -> facet.values.apply(contract).stream())
              .collect(groupingBy(Function.identity(), LinkedHashMap::new, counting())));
    }
    return counts;
  }

  // -- STEPS --

  private List<CatalogConnectorInjectorContract> matching(
      String tenantId, SearchPaginationInput input) {
    if (!Facet.answers(input)) {
      return List.of();
    }
    SearchPaginationInput translated = translateFields(input, Facet.FIELDS);
    Specification<CatalogConnectorInjectorContract> filters =
        computeFilterGroupJpa(translated.getFilterGroup());
    return catalogConnectorInjectorContractRepository.findAll(
        ofInjectorsNotRegisteredIn(tenantId)
            .and(filters)
            .and(computeSearchJpa(translated.getTextSearch())),
        BY_INTEGRATION);
  }
}
