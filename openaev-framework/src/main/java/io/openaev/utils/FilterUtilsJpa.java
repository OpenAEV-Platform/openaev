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

public class FilterUtilsJpa {

  private FilterUtilsJpa() {}

  public static final int PAGE_NUMBER_OPTION = 0;
  public static final int PAGE_SIZE_OPTION = 100;

  public record Option(String id, String label) {}

  public static final Specification<?> EMPTY_SPECIFICATION = (root, query, cb) -> cb.conjunction();

  @SuppressWarnings("unchecked")
  public static <T> Specification<T> computeFilterGroupJpa(
      @Nullable final FilterGroup filterGroup) {
    return computeFilterGroupJpa(filterGroup, new HashMap<>());
  }

  @SuppressWarnings("unchecked")
  public static <T> Specification<T> computeFilterGroupJpa(
      @Nullable final FilterGroup filterGroup, Map<String, Join<Base, Base>> joinMap) {
    if (filterGroup == null) {
      return (Specification<T>) EMPTY_SPECIFICATION;
    }
    List<Filter> filters = Optional.ofNullable(filterGroup.getFilters()).orElse(new ArrayList<>());
    FilterMode mode = Optional.ofNullable(filterGroup.getMode()).orElse(and);

    if (!filters.isEmpty()) {
      List<Specification<T>> list =
          filters.stream()
              .map(
                  (Function<? super Filter, Specification<T>>)
                      f -> FilterUtilsJpa.computeFilter(f, joinMap))
              .toList();
      Specification<T> result = null;
      for (Specification<T> el : list) {
        if (result == null) {
          result = el;
        } else {
          if (or.equals(mode)) {
            result = result.or(el);
          } else {
            // Default case
            result = result.and(el);
          }
        }
      }
      return result;
    }
    return (Specification<T>) EMPTY_SPECIFICATION;
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
        return cb.or(predicates.toArray(Predicate[]::new));
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

  private static <U> Predicate toPredicate(
      @NotNull final Expression<U> paths,
      @NotNull final Filter filter,
      @NotNull final CriteriaBuilder cb,
      @NotNull final Class<?> type) {
    BiFunction<Expression<U>, List<String>, Predicate> operation =
        computeOperation(filter.getOperator(), cb, type);
    return operation.apply(paths, filter.getValues());
  }

  // -- OPERATOR --

  private static <U> BiFunction<Expression<U>, List<String>, Predicate> computeOperation(
      @Nullable final FilterOperator operator,
      @NotNull final CriteriaBuilder cb,
      @NotNull final Class<?> type) {
    if (operator == null) {
      throw new IllegalArgumentException("Operator cannot be null");
    }
    if (operator.equals(FilterOperator.not_contains)) {
      return (Expression<U> paths, List<String> texts) ->
          notContainsTexts((Expression<String>) paths, cb, texts, type);
    } else if (operator.equals(FilterOperator.contains)) {
      return (Expression<U> paths, List<String> texts) ->
          containsTexts((Expression<String>) paths, cb, texts, type);
    } else if (operator.equals(FilterOperator.not_starts_with)) {
      return (Expression<U> paths, List<String> texts) ->
          notStartWithTexts((Expression<String>) paths, cb, texts, type);
    } else if (operator.equals(FilterOperator.starts_with)) {
      return (Expression<U> paths, List<String> texts) ->
          startWithTexts((Expression<String>) paths, cb, texts, type);
    } else if (operator.equals(FilterOperator.empty)) {
      return (Expression<U> paths, List<String> texts) ->
          empty((Expression<String>) paths, cb, type);
    } else if (operator.equals(FilterOperator.not_empty)) {
      return (Expression<U> paths, List<String> texts) ->
          notEmpty((Expression<String>) paths, cb, type);
    } else if (operator.equals(FilterOperator.gt)) {
      return (Expression<U> paths, List<String> texts) ->
          greaterThanTexts((Expression<Instant>) paths, cb, texts);
    } else if (operator.equals(FilterOperator.gte)) {
      return (Expression<U> paths, List<String> texts) ->
          greaterThanOrEqualTexts((Expression<Instant>) paths, cb, texts);
    } else if (operator.equals(FilterOperator.lt)) {
      return (Expression<U> paths, List<String> texts) ->
          lessThanTexts((Expression<Instant>) paths, cb, texts);
    } else if (operator.equals(FilterOperator.lte)) {
      return (Expression<U> paths, List<String> texts) ->
          lessThanOrEqualTexts((Expression<Instant>) paths, cb, texts);
    } else if (operator.equals(FilterOperator.not_eq)) {
      return (Expression<U> paths, List<String> texts) ->
          notEqualsTexts((Expression<String>) paths, cb, texts, type);
    } else { // Default case -> equals
      return (Expression<U> paths, List<String> texts) ->
          equalsTexts((Expression<String>) paths, cb, texts, type);
    }
  }
}
