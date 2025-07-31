package io.openbas.utils.fixtures;

import io.openbas.database.model.Filters;
import io.openbas.rest.injector_contract.input.SearchPaginationWithSerialisationOptionsInput;
import io.openbas.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

public class PaginationFixture {

  public static SearchPaginationInput.SearchPaginationInputBuilder getDefault() {
    return SearchPaginationInput.builder().page(0).size(10);
  }

  public static SearchPaginationWithSerialisationOptionsInput getOptioned() {
    SearchPaginationWithSerialisationOptionsInput si =
        new SearchPaginationWithSerialisationOptionsInput();
    si.setPage(0);
    si.setSize(10);
    return si;
  }

  public static SearchPaginationInput simpleSearchWithAndOperator(
      String key, String value, Filters.FilterOperator operator) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setValues(value == null ? List.of() : List.of(value));
    filter.setOperator(operator);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(new ArrayList<>(List.of(filter)));
    return getDefault().filterGroup(filterGroup).build();
  }

  public static SearchPaginationInput simpleSearchWithOrOperator(
      String key, String value, Filters.FilterOperator operator) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setValues(value == null ? List.of() : List.of(value));
    filter.setOperator(operator);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.or);
    filterGroup.setFilters(new ArrayList<>(List.of(filter)));
    return getDefault().filterGroup(filterGroup).build();
  }

  public static SearchPaginationWithSerialisationOptionsInput optionedSearchWithAndOperator(
      String key, String value, Filters.FilterOperator operator) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setValues(value == null ? List.of() : List.of(value));
    filter.setOperator(operator);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(new ArrayList<>(List.of(filter)));
    SearchPaginationWithSerialisationOptionsInput si = getOptioned();
    si.setFilterGroup(filterGroup);
    return si;
  }

  public static SearchPaginationWithSerialisationOptionsInput optionedSearchWithOrOperator(
      String key, String value, Filters.FilterOperator operator) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setValues(value == null ? List.of() : List.of(value));
    filter.setOperator(operator);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.or);
    filterGroup.setFilters(new ArrayList<>(List.of(filter)));
    SearchPaginationWithSerialisationOptionsInput si = getOptioned();
    si.setFilterGroup(filterGroup);
    return si;
  }

  public static <T> Page<T> pagedOutput(List<T> output) {
    return new PageImpl<>(output);
  }
}
