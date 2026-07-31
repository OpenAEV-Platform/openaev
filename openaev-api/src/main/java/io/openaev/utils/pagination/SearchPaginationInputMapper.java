package io.openaev.utils.pagination;

import io.openaev.database.model.Filters;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Utility to translate filter/sort field names in {@link SearchPaginationInput}.
 *
 * <p>This keeps pagination and text search untouched while mapping:
 *
 * <ul>
 *   <li>{@code Filters.Filter.key}
 *   <li>{@code SortField.property}
 * </ul>
 */
public final class SearchPaginationInputMapper {

  private SearchPaginationInputMapper() {}

  public static SearchPaginationInput translateFields(
      @NotNull final SearchPaginationInput input, @NotNull final Map<String, String> fieldMapping) {
    SearchPaginationInput translated = new SearchPaginationInput();
    translated.setPage(input.getPage());
    translated.setSize(input.getSize());
    translated.setTextSearch(input.getTextSearch());

    translated.setFilterGroup(translateFilterGroup(input.getFilterGroup(), fieldMapping));

    if (input.getSorts() == null) {
      translated.setSorts(null);
    } else {
      translated.setSorts(
          input.getSorts().stream()
              .map(
                  sort ->
                      new SortField(
                          mapField(sort.property(), fieldMapping),
                          sort.direction(),
                          sort.nullHandling()))
              .toList());
    }

    return translated;
  }

  private static Filters.FilterGroup translateFilterGroup(
      Filters.FilterGroup filterGroup, Map<String, String> fieldMapping) {
    if (filterGroup == null) {
      return null;
    }

    Filters.FilterGroup translated = new Filters.FilterGroup();
    translated.setMode(filterGroup.getMode());

    if (filterGroup.getFilters() == null) {
      translated.setFilters(List.of());
      return translated;
    }

    translated.setFilters(
        filterGroup.getFilters().stream()
            .map(
                filter ->
                    new Filters.Filter(
                        filter.getId(),
                        mapField(filter.getKey(), fieldMapping),
                        filter.getMode(),
                        filter.getValues(),
                        filter.getOperator()))
            .toList());

    return translated;
  }

  private static String mapField(String field, Map<String, String> fieldMapping) {
    if (field == null) {
      return null;
    }
    return fieldMapping.getOrDefault(field, field);
  }
}
