package io.openaev.utils;

import static io.openaev.database.model.Filters.FilterMode.and;
import static io.openaev.database.model.Filters.FilterMode.or;
import static io.openaev.schema.SchemaUtils.getFilterableProperties;
import static io.openaev.schema.SchemaUtils.retrieveProperty;
import static io.openaev.utils.JpaUtils.toPath;
import static io.openaev.utils.OperationUtilsJpa.*;

import io.openaev.database.model.Base;
import io.openaev.database.model.Filters.Filter;
import io.openaev.database.model.Filters.FilterGroup;
import io.openaev.database.model.Filters.FilterMode;
import io.openaev.database.model.Filters.FilterOperator;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Utility class for building JPA Specifications from filter groups.
 *
 * <p>This class provides methods to convert filter definitions into JPA Specifications that can be
 * used with Spring Data repositories for dynamic querying.
 *
 * <p>Supported filter operators:
 *
 * <ul>
 *   <li>{@code eq} - Equals
 *   <li>{@code not_eq} - Not equals
 *   <li>{@code contains} - Contains substring
 *   <li>{@code not_contains} - Does not contain substring
 *   <li>{@code starts_with} - Starts with prefix
 *   <li>{@code not_starts_with} - Does not start with prefix
 *   <li>{@code empty} - Is null or empty
 *   <li>{@code not_empty} - Is not null or empty
 *   <li>{@code gt}, {@code gte}, {@code lt}, {@code lte} - Date/time comparisons
 * </ul>
 */
public final class FilterUtilsJpa {

  private FilterUtilsJpa() {
    // Utility class - prevent instantiation
  }

  /** Default page number for pagination. */
  public static final int PAGE_NUMBER_OPTION = 0;

  /** Default page size for pagination. */
  public static final int PAGE_SIZE_OPTION = 100;

  /**
   * Represents a selectable option with an ID and display label.
   *
   * @param id the option identifier
   * @param label the display label
   */
  public record Option(String id, String label) {}

  /** Empty specification that matches all records. */
  public static final Specification<?> EMPTY_SPECIFICATION = (root, query, cb) -> cb.conjunction();

  /**
   * Computes a JPA Specification from a filter group.
   *
   * @param filterGroup the filter group to convert (may be null)
   * @param <T> the entity type
   * @return a Specification representing the filter group, or an empty specification if null
   */
  @SuppressWarnings("unchecked")
  public static <T> Specification<T> computeFilterGroupJpa(
      @Nullable final FilterGroup filterGroup) {
    return computeFilterGroupJpa(filterGroup, new HashMap<>());
  }

  /**
   * Computes a JPA Specification from a filter group with existing joins.
   *
   * @param filterGroup the filter group to convert (may be null)
   * @param joinMap map of existing joins to reuse
   * @param <T> the entity type
   * @return a Specification representing the filter group, or an empty specification if null
   */
  @SuppressWarnings("unchecked")
  public static <T> Specification<T> computeFilterGroupJpa(
      @Nullable final FilterGroup filterGroup, Map<String, Join<Base, Base>> joinMap) {
    if (filterGroup == null) {
      return (Specification<T>) EMPTY_SPECIFICATION;
    }

    List<Filter> filters = Optional.ofNullable(filterGroup.getFilters()).orElse(List.of());
    if (filters.isEmpty()) {
      return (Specification<T>) EMPTY_SPECIFICATION;
    }

    FilterMode mode = Optional.ofNullable(filterGroup.getMode()).orElse(and);
    List<Specification<T>> specifications =
        filters.stream()
            .map(
                (Function<? super Filter, Specification<T>>)
                    f -> FilterUtilsJpa.computeFilter(f, joinMap))
            .toList();

    return combineSpecifications(specifications, mode);
  }

  /**
   * Combines multiple specifications using the specified mode.
   *
   * @param specifications the specifications to combine
   * @param mode the combination mode (AND or OR)
   * @param <T> the entity type
   * @return the combined specification
   */
  @SuppressWarnings("unchecked")
  private static <T> Specification<T> combineSpecifications(
      List<Specification<T>> specifications, FilterMode mode) {
    if (specifications.isEmpty()) {
      return (Specification<T>) EMPTY_SPECIFICATION;
    }

    Specification<T> result = specifications.get(0);
    for (int i = 1; i < specifications.size(); i++) {
      Specification<T> current = specifications.get(i);
      result = or.equals(mode) ? result.or(current) : result.and(current);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static <T, U> Specification<T> computeFilter(
      @Nullable final Filter filter, Map<String, Join<Base, Base>> joinMap) {
    if (filter == null) {
      return (Specification<T>) EMPTY_SPECIFICATION;
    }
    String filterKey = filter.getKey();

    return (root, query, cb) -> {
      List<PropertySchema> propertySchemas;
      try {
        propertySchemas = SchemaUtils.schemaWithSubtypes(root.getJavaType());
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
      List<PropertySchema> filterableProperties = getFilterableProperties(propertySchemas);
      PropertySchema filterableProperty = retrieveProperty(filterableProperties, filterKey);

      // multiple paths case
      if (filterableProperty.getPaths().length > 1) {
        List<Predicate> predicates = new ArrayList<>();
        for (String path : filterableProperty.getPaths()) {
          PropertySchema singlePathPropertySchema =
              PropertySchema.builder()
                  .name(filterableProperty.getName())
                  .type(filterableProperty.getType())
                  .path(path)
                  .build();
          Expression<U> paths = toPath(singlePathPropertySchema, root, joinMap);
          predicates.add(
              toPredicate(
                  paths,
                  filter,
                  cb,
                  filterableProperty.getJoinTable() != null
                      ? String.class
                      : filterableProperty.getType()));
        }
        if (filter.getOperator().equals(FilterOperator.not_contains)
            || filter.getOperator().equals(FilterOperator.not_eq)
            || filter.getOperator().equals(FilterOperator.not_starts_with)
            || filter.getOperator().equals(FilterOperator.empty)) {
          return cb.and(predicates.toArray(Predicate[]::new));
        } else {
          return cb.or(predicates.toArray(Predicate[]::new));
        }
      }

      // Single path or no path case
      Expression<U> paths = toPath(filterableProperty, root, joinMap);
      // In case of join table, we will use ID so type is String
      return toPredicate(
          paths,
          filter,
          cb,
          filterableProperty.getJoinTable() != null ? String.class : filterableProperty.getType());
    };
  }

  public static <U> Predicate toPredicate(
      @NotNull final Expression<U> paths,
      @NotNull final Filter filter,
      @NotNull final CriteriaBuilder cb,
      @NotNull final Class<?> type) {
    FilterOperator operator = filter.getOperator();
    if (operator == null) {
      operator = FilterOperator.eq;
    }
    BiFunction<Expression<U>, List<String>, Predicate> operation =
        computeOperation(operator, cb, type);
    return operation.apply(paths, filter.getValues());
  }

  // -- OPERATOR --

  @SuppressWarnings("unchecked")
  private static <U> BiFunction<Expression<U>, List<String>, Predicate> computeOperation(
      @NotNull final FilterOperator operator,
      @NotNull final CriteriaBuilder cb,
      @NotNull final Class<?> type) {
    return switch (operator) {
      case not_contains ->
          (paths, texts) -> notContainsTexts((Expression<String>) paths, cb, texts, type);
      case contains -> (paths, texts) -> containsTexts((Expression<String>) paths, cb, texts, type);
      case not_starts_with ->
          (paths, texts) -> notStartWithTexts((Expression<String>) paths, cb, texts, type);
      case starts_with ->
          (paths, texts) -> startWithTexts((Expression<String>) paths, cb, texts, type);
      case empty -> (paths, texts) -> empty((Expression<String>) paths, cb, type);
      case not_empty -> (paths, texts) -> notEmpty((Expression<String>) paths, cb, type);
      case gt -> (paths, texts) -> greaterThanTexts((Expression<Instant>) paths, cb, texts);
      case gte -> (paths, texts) -> greaterThanOrEqualTexts((Expression<Instant>) paths, cb, texts);
      case lt -> (paths, texts) -> lessThanTexts((Expression<Instant>) paths, cb, texts);
      case lte -> (paths, texts) -> lessThanOrEqualTexts((Expression<Instant>) paths, cb, texts);
      case not_eq -> (paths, texts) -> notEqualsTexts((Expression<String>) paths, cb, texts, type);
      default -> (paths, texts) -> equalsTexts((Expression<String>) paths, cb, texts, type);
    };
  }
}
