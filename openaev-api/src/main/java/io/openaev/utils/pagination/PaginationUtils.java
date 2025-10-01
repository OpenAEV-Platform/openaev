package io.openaev.utils.pagination;

import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static io.openaev.utils.pagination.SortUtilsJpa.toSortJpa;

import io.openaev.database.model.Base;
import jakarta.persistence.criteria.Join;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public class PaginationUtils {

  private PaginationUtils() {}

  // -- JPA --

  public static <T> Pageable buildPageable(
      @NotNull final SearchPaginationInput input, @NotNull final Class<T> clazz) {
    return PageRequest.of(input.getPage(), input.getSize(), toSortJpa(input.getSorts(), clazz));
  }

  public static <T> Page<T> buildPaginationJPA(
      @NotNull final BiFunction<Specification<T>, Pageable, Page<T>> findAll,
      @NotNull final SearchPaginationInput input,
      @NotNull final Class<T> clazz) {
    // Specification
    Specification<T> filterSpecifications = computeFilterGroupJpa(input.getFilterGroup());
    Specification<T> searchSpecifications = computeSearchJpa(input.getTextSearch());

    // Pageable
    Pageable pageable = buildPageable(input, clazz);

    return findAll.apply(filterSpecifications.and(searchSpecifications), pageable);
  }

  // -- CRITERIA BUILDER --

  public static <T, U> Page<U> buildPaginationCriteriaBuilder(
      @NotNull final TriFunction<Specification<T>, Specification<T>, Pageable, Page<U>> findAll,
      @NotNull final SearchPaginationInput input,
      @NotNull final Class<T> clazz,
      Map<String, Join<Base, Base>> joinMap) {
    // Specification
    Specification<T> filterSpecifications = computeFilterGroupJpa(input.getFilterGroup(), joinMap);
    Specification<T> filterSpecificationsForCount =
        computeFilterGroupJpa(input.getFilterGroup(), new HashMap<>());
    Specification<T> searchSpecifications = computeSearchJpa(input.getTextSearch());

    // Pageable
    Pageable pageable =
        PageRequest.of(input.getPage(), input.getSize(), toSortJpa(input.getSorts(), clazz));

    return findAll.apply(
        filterSpecifications.and(searchSpecifications), filterSpecificationsForCount, pageable);
  }

  public static <T, U> Page<U> buildPaginationCriteriaBuilder(
      @NotNull final TriFunction<Specification<T>, Specification<T>, Pageable, Page<U>> findAll,
      @NotNull final SearchPaginationInput input,
      @NotNull final Class<T> clazz) {
    return buildPaginationCriteriaBuilder(findAll, input, clazz, new HashMap<>());
  }

  /**
   * Build PaginationJPA with a specified search specifications that replace the default ones
   *
   * @param findAll the find all method
   * @param input the search inputs
   * @param clazz the class that we're looking for
   * @param specificSearchSpecification the specified search specification (will replace the default
   *     ones)
   * @return a Page of results
   */
  public static <T> Page<T> buildPaginationJPA(
      @NotNull final BiFunction<Specification<T>, Pageable, Page<T>> findAll,
      @NotNull final SearchPaginationInput input,
      @NotNull final Class<T> clazz,
      Specification<T> specificSearchSpecification) {
    // Specification
    Specification<T> filterSpecifications = computeFilterGroupJpa(input.getFilterGroup());

    // Pageable
    Pageable pageable =
        PageRequest.of(input.getPage(), input.getSize(), toSortJpa(input.getSorts(), clazz));

    return findAll.apply(filterSpecifications.and(specificSearchSpecification), pageable);
  }
}
