package io.openaev.rest.inject.utils;

import static java.util.Optional.ofNullable;

import io.openaev.database.model.Filters;
import io.openaev.database.model.Inject;
import io.openaev.database.specification.InjectSpecification;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.data.jpa.domain.Specification;

/** Inject filters that the generic filter mechanics cannot express on their own. */
public class InjectFilterUtils {

  public static final String INJECT_STATUS_FILTER = "inject_status";

  private InjectFilterUtils() {}

  /**
   * Manage the {@code inject_status} filter: an inject never launched has no status row, so the
   * DRAFT value it is serialized with matches nothing on a plain {@code status.name} comparison,
   * and a negative filter silently drops those injects. The filter is stripped from the generic
   * group and re-expressed as a Specification, combined with the group's AND/OR mode.
   */
  public static UnaryOperator<Specification<Inject>> handleCustomFilter(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    Specification<Inject> customSpecification = executionStatusSpecification(searchPaginationInput);
    if (customSpecification == null) {
      return (Specification<Inject> specification) -> specification;
    }
    // Combined like any other filter of the group. Anything but an explicit "or" is an AND: the
    // filter has been stripped from the group, dropping it here would silently widen the search.
    return Filters.FilterMode.or.equals(searchPaginationInput.getFilterGroup().getMode())
        ? customSpecification::or
        : customSpecification::and;
  }

  /** Strips and maps the {@code inject_status} filter to a Specification (or null). */
  private static Specification<Inject> executionStatusSpecification(
      final SearchPaginationInput searchPaginationInput) {
    Optional<Filters.Filter> filterOpt =
        ofNullable(searchPaginationInput.getFilterGroup())
            .flatMap(f -> f.findByKey(INJECT_STATUS_FILTER));
    if (filterOpt.isEmpty()) {
      return null;
    }
    Filters.Filter filter = filterOpt.get();
    List<String> values = filter.getValues();
    Filters.FilterOperator operator =
        ofNullable(filter.getOperator()).orElse(Filters.FilterOperator.eq);
    // empty / not_empty keep their generic meaning (with or without a status row at all)
    boolean handled =
        Filters.FilterOperator.eq.equals(operator)
            || Filters.FilterOperator.not_eq.equals(operator);
    if (!handled || values == null || values.isEmpty()) {
      return null;
    }
    searchPaginationInput.getFilterGroup().removeByKey(INJECT_STATUS_FILTER);
    return InjectSpecification.hasExecutionStatusNames(
        values, Filters.FilterOperator.not_eq.equals(operator));
  }
}
