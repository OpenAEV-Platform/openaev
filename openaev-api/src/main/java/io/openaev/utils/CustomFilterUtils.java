package io.openaev.utils;

import io.openaev.database.model.Base;
import io.openaev.database.model.Filters;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

/**
 * Utility class for building custom filter specifications.
 *
 * <p>Provides methods for computing filter combination modes (AND/OR) when building JPA
 * specifications for database queries.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.Filters
 * @see org.springframework.data.jpa.domain.Specification
 */
public class CustomFilterUtils {

  private CustomFilterUtils() {}

  /**
   * Computes the appropriate specification combiner based on the filter mode.
   *
   * <p>Returns a function that combines specifications using either AND or OR logic based on the
   * filter group's mode setting. If no mode is specified, returns an identity function.
   *
   * @param <T> the entity type extending Base
   * @param searchPaginationInput the pagination input containing the filter group
   * @param customSpecification the custom specification to combine with
   * @return a unary operator that applies the appropriate combination logic
   */
  public static <T extends Base> UnaryOperator<Specification<T>> computeMode(
      @NotNull final SearchPaginationInput searchPaginationInput,
      Specification<T> customSpecification) {
    if (Filters.FilterMode.and.equals(searchPaginationInput.getFilterGroup().getMode())) {
      return customSpecification::and;
    } else if (Filters.FilterMode.or.equals(searchPaginationInput.getFilterGroup().getMode())) {
      return customSpecification::or;
    } else {
      return (Specification<T> specification) -> specification;
    }
  }
}
