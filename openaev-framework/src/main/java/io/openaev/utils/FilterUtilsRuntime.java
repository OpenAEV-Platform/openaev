package io.openaev.utils;

import static io.openaev.database.model.Filters.FilterMode.and;
import static io.openaev.database.model.Filters.FilterMode.or;
import static io.openaev.schema.SchemaUtils.getFilterableProperties;
import static io.openaev.schema.SchemaUtils.retrieveProperty;

import io.openaev.database.model.Filters.Filter;
import io.openaev.database.model.Filters.FilterGroup;
import io.openaev.database.model.Filters.FilterMode;
import io.openaev.database.model.Filters.FilterOperator;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import jakarta.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Utility class for building runtime predicates from filter groups.
 *
 * <p>This class provides methods to convert filter definitions into Java Predicates that can be
 * used for in-memory filtering of collections. This is useful when filtering needs to happen
 * outside of the database layer.
 *
 * <p>Supported filter operators:
 *
 * <ul>
 *   <li>{@code eq} - Equals (case-insensitive)
 *   <li>{@code not_eq} - Not equals
 *   <li>{@code contains} - Contains substring (case-insensitive)
 *   <li>{@code not_contains} - Does not contain substring
 *   <li>{@code starts_with} - Starts with prefix (case-insensitive)
 *   <li>{@code not_starts_with} - Does not start with prefix
 *   <li>{@code empty} - Is null or blank
 *   <li>{@code not_empty} - Is not null or blank
 * </ul>
 *
 * @see FilterUtilsJpa for JPA-based filtering
 */
public final class FilterUtilsRuntime {

  private FilterUtilsRuntime() {
    // Utility class - prevent instantiation
  }

  /** Predicate that always returns true (matches all values). */
  private static final Predicate<Object> EMPTY_PREDICATE = value -> true;

  public static Predicate<Object> computeFilterGroupRuntime(
      @Nullable final FilterGroup filterGroup) {
    if (filterGroup == null) {
      return EMPTY_PREDICATE;
    }
    List<Filter> filters = Optional.ofNullable(filterGroup.getFilters()).orElse(new ArrayList<>());
    FilterMode mode = Optional.ofNullable(filterGroup.getMode()).orElse(and);

    if (!filters.isEmpty()) {
      List<Predicate<Object>> list =
          filters.stream().map(FilterUtilsRuntime::computeFilter).toList();
      Predicate<Object> result = null;
      for (Predicate<Object> el : list) {
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
    return EMPTY_PREDICATE;
  }

  private static Predicate<Object> computeFilter(@Nullable final Filter filter) {
    if (filter == null) {
      return EMPTY_PREDICATE;
    }
    String filterKey = filter.getKey();
    List<String> filterValues = filter.getValues();

    if (filterValues == null || filterValues.isEmpty()) {
      return EMPTY_PREDICATE;
    }

    return (value) -> {
      List<PropertySchema> propertySchemas = SchemaUtils.schema(value.getClass());
      List<PropertySchema> filterableProperties = getFilterableProperties(propertySchemas);
      PropertySchema filterableProperty = retrieveProperty(filterableProperties, filterKey);
      Map.Entry<Class<Object>, Object> entry = getPropertyInfo(value, filterableProperty);
      return getPropertyValue(entry, filter);
    };
  }

  @SuppressWarnings("unchecked")
  private static boolean getPropertyValue(Map.Entry<Class<Object>, Object> entry, Filter filter) {
    if (entry == null || entry.getValue() == null) {
      return false;
    }

    BiFunction<Object, List<String>, Boolean> operation = computeOperation(filter.getOperator());

    if (entry.getKey().isAssignableFrom(Map.class)
        || entry.getKey().getName().contains("ImmutableCollections")) {
      return ((Map) entry.getValue())
          .values().stream().anyMatch(v -> operation.apply(v, filter.getValues()));
    } else if (entry.getKey().isArray()) {
      return Arrays.stream(((Object[]) entry.getValue()))
          .anyMatch(v -> operation.apply(v, filter.getValues()));
    } else {
      return operation.apply(entry.getValue(), filter.getValues());
    }
  }

  @SuppressWarnings("unchecked")
  private static Map.Entry<Class<Object>, Object> getPropertyInfo(
      Object obj, PropertySchema propertySchema) {
    if (obj == null) {
      return null;
    }

    Field field;
    Object currentObject;
    try {
      field = obj.getClass().getDeclaredField(propertySchema.getName());
      field.setAccessible(true);

      currentObject = field.get(obj);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    if (currentObject == null) {
      return null;
    }

    return Map.entry((Class<Object>) currentObject.getClass(), currentObject);
  }

  // -- OPERATOR --

  private static BiFunction<Object, List<String>, Boolean> computeOperation(
      @Nullable final FilterOperator operator) {
    if (operator == null) {
      return OperationUtilsRuntime::equalsTexts;
    }

    return switch (operator) {
      case not_contains -> OperationUtilsRuntime::notContainsTexts;
      case contains -> OperationUtilsRuntime::containsTexts;
      case not_starts_with -> OperationUtilsRuntime::notStartWithTexts;
      case starts_with -> OperationUtilsRuntime::startWithTexts;
      case not_eq -> OperationUtilsRuntime::notEqualsTexts;
      case empty -> (value, texts) -> OperationUtilsRuntime.empty(value);
      case not_empty -> (value, texts) -> OperationUtilsRuntime.notEmpty(value);
      default -> OperationUtilsRuntime::equalsTexts;
    };
  }
}
