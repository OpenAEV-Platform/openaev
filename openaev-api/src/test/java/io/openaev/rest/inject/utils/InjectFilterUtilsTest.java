package io.openaev.rest.inject.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Filters;
import io.openaev.database.model.Inject;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

@DisplayName("Inject custom filters")
class InjectFilterUtilsTest {

  private static SearchPaginationInput inputWith(Filters.Filter... filters) {
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(new ArrayList<>(List.of(filters)));
    return SearchPaginationInput.builder().page(0).size(10).filterGroup(filterGroup).build();
  }

  private static Filters.Filter filter(
      String key, Filters.FilterOperator operator, String... values) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setOperator(operator);
    filter.setValues(List.of(values));
    return filter;
  }

  @Test
  @DisplayName("An inject_status filter is stripped from the group and re-expressed")
  void given_statusFilter_should_stripItAndReturnSpecification() {
    SearchPaginationInput input =
        inputWith(
            filter("inject_status", Filters.FilterOperator.eq, "DRAFT"),
            filter("inject_title", Filters.FilterOperator.contains, "test"));

    UnaryOperator<Specification<Inject>> operator = InjectFilterUtils.handleCustomFilter(input);

    assertThat(input.getFilterGroup().findByKey("inject_status")).isEmpty();
    assertThat(input.getFilterGroup().findByKey("inject_title")).isPresent();
    Specification<Inject> generic = Specification.unrestricted();
    assertThat(operator.apply(generic)).isNotSameAs(generic);
  }

  @Test
  @DisplayName("Other filters are left to the generic mechanics")
  void given_noStatusFilter_should_returnIdentity() {
    SearchPaginationInput input =
        inputWith(filter("inject_title", Filters.FilterOperator.contains, "test"));

    Specification<Inject> generic = Specification.unrestricted();
    assertThat(InjectFilterUtils.handleCustomFilter(input).apply(generic)).isSameAs(generic);
    assertThat(input.getFilterGroup().findByKey("inject_title")).isPresent();
  }

  @Test
  @DisplayName("Emptiness operators keep their generic meaning")
  void given_emptyOperator_should_returnIdentity() {
    SearchPaginationInput input = inputWith(filter("inject_status", Filters.FilterOperator.empty));

    Specification<Inject> generic = Specification.unrestricted();
    assertThat(InjectFilterUtils.handleCustomFilter(input).apply(generic)).isSameAs(generic);
    assertThat(input.getFilterGroup().findByKey("inject_status")).isPresent();
  }

  @Test
  @DisplayName("A valueless status filter is left untouched")
  void given_statusFilterWithoutValue_should_returnIdentity() {
    SearchPaginationInput input = inputWith(filter("inject_status", Filters.FilterOperator.eq));

    Specification<Inject> generic = Specification.unrestricted();
    assertThat(InjectFilterUtils.handleCustomFilter(input).apply(generic)).isSameAs(generic);
    assertThat(input.getFilterGroup().findByKey("inject_status")).isPresent();
  }

  @Test
  @DisplayName("No filter group at all is supported")
  void given_noFilterGroup_should_returnIdentity() {
    SearchPaginationInput input = SearchPaginationInput.builder().page(0).size(10).build();

    Specification<Inject> generic = Specification.unrestricted();
    assertThat(InjectFilterUtils.handleCustomFilter(input).apply(generic)).isSameAs(generic);
  }
}
