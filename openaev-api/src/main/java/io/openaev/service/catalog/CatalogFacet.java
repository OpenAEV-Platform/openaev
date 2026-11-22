package io.openaev.service.catalog;

import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.pagination.SearchPaginationInputMapper.withoutFilter;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;

import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;

/** A facet of an Available tab: the filter key it counts, and how it counts its values. */
public sealed interface CatalogFacet {

  String PLATFORMS_KEY = "catalog_item_platforms";
  String DOMAIN_KEY = "catalog_item_domain";
  String SCENARIO_TYPE_KEY = "catalog_item_scenario_type";
  String CATEGORY_KEY = "catalog_item_category";
  String AUTHOR_KEY = "catalog_item_author";
  String INSTALLATION_KEY = "catalog_item_installation";

  CatalogFacet PLATFORMS =
      new EachValue(
          PLATFORMS_KEY,
          Stream.of(
                  Endpoint.PLATFORM_TYPE.Windows,
                  Endpoint.PLATFORM_TYPE.Linux,
                  Endpoint.PLATFORM_TYPE.MacOS)
              .map(Enum::name)
              .toList());

  String key();

  /** What a filter on this facet keeps when no column of the items holds it. */
  default Specification<CatalogItem> condition(SearchPaginationInput input) {
    return Specification.unrestricted();
  }

  /** The input left for the column filters once this facet took its own. */
  default SearchPaginationInput columnFilters(SearchPaginationInput input) {
    return input;
  }

  /** The number of listed items per value. */
  <T extends CatalogItem> Map<String, Long> count(
      CatalogFacetCounter counter, Class<T> type, Specification<CatalogItem> listed);

  /** A column holding one value: its values come from the items, in one grouped query. */
  record Grouped(String key) implements CatalogFacet {

    @Override
    public <T extends CatalogItem> Map<String, Long> count(
        CatalogFacetCounter counter, Class<T> type, Specification<CatalogItem> listed) {
      return counter.countByValue(type, listed, key);
    }
  }

  /** A column holding a list: each known value counted with the filter its facet row sets. */
  record EachValue(String key, List<String> values) implements CatalogFacet {

    @Override
    public <T extends CatalogItem> Map<String, Long> count(
        CatalogFacetCounter counter, Class<T> type, Specification<CatalogItem> listed) {
      Map<String, Long> counts = new LinkedHashMap<>();
      values.forEach(value -> counts.put(value, counter.count(listed.and(only(value)))));
      return counts;
    }

    private Specification<CatalogItem> only(String value) {
      return computeFilterGroupJpa(
          Filters.FilterGroup.builder()
              .filters(
                  List.of(Filters.Filter.builder().id(key).key(key).values(List.of(value)).build()))
              .build());
    }
  }

  /** No column: each value is a condition on the items, the same for the filter and the count. */
  record Condition(String key, Map<String, Specification<CatalogItem>> values)
      implements CatalogFacet {

    @Override
    public <T extends CatalogItem> Map<String, Long> count(
        CatalogFacetCounter counter, Class<T> type, Specification<CatalogItem> listed) {
      Map<String, Long> counts = new LinkedHashMap<>();
      values.forEach((value, condition) -> counts.put(value, counter.count(listed.and(condition))));
      return counts;
    }

    // Any of the values chosen; an unknown value matches nothing.
    @Override
    public Specification<CatalogItem> condition(SearchPaginationInput input) {
      return chosen(input, key).stream()
          .map(value -> values.getOrDefault(value, (root, query, cb) -> cb.disjunction()))
          .reduce(Specification::or)
          .orElse(Specification.unrestricted());
    }

    @Override
    public SearchPaginationInput columnFilters(SearchPaginationInput input) {
      return withoutFilter(input, key);
    }
  }

  /** The items the filters and search of the input keep, each facet taking its own filter. */
  static Specification<CatalogItem> matching(
      SearchPaginationInput input, List<CatalogFacet> facets) {
    SearchPaginationInput columns = input;
    Specification<CatalogItem> conditions = Specification.unrestricted();
    for (CatalogFacet facet : facets) {
      conditions = conditions.and(facet.condition(input));
      columns = facet.columnFilters(columns);
    }
    Specification<CatalogItem> filters = computeFilterGroupJpa(columns.getFilterGroup());
    Specification<CatalogItem> search = computeSearchJpa(columns.getTextSearch());
    return filters.and(search).and(conditions);
  }

  private static List<String> chosen(SearchPaginationInput input, String key) {
    return Optional.ofNullable(input.getFilterGroup())
        .map(Filters.FilterGroup::getFilters)
        .orElse(List.of())
        .stream()
        .filter(filter -> key.equals(filter.getKey()))
        .flatMap(filter -> filter.getValues().stream())
        .toList();
  }
}
