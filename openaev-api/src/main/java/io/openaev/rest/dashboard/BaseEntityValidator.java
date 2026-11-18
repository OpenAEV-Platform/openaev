package io.openaev.rest.dashboard;

import io.openaev.annotation.Queryable;
import io.openaev.database.model.Filters;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.RetiredIndexes;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.StructuralHistogramWidget;
import io.openaev.engine.api.WidgetConfiguration;
import io.openaev.engine.api.WidgetConfigurationWithSeries;
import io.openaev.rest.exception.BadRequestException;
import jakarta.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Rejects a widget whose {@code base_entity} filter names no engine model, or whose structural
 * histogram groups by a field the base entity's search document does not carry.
 *
 * <p>Every widget query runs against the {@code <prefix>_*} index pattern, so a {@code base_entity}
 * value that matches no model (a typo, or a retired name such as {@code endpoint}) does not fail:
 * it simply matches no document, and the widget answers 0. A caller reading "0 assets" cannot tell
 * that from an empty inventory, so the value is refused with HTTP 400 instead, naming it, the
 * successor of a retired name and the valid values.
 *
 * <p>The group-by {@code field} of a structural histogram is a terms aggregation on the search
 * index: a field the document does not carry - for instance {@code finding_assets}, a filter of the
 * SQL findings list that is not indexed - aggregates nothing and the widget answers an empty
 * series. It is refused the same way, naming the indexed equivalent of a known SQL-only key and the
 * valid group-by fields of the entity.
 */
@Component
@RequiredArgsConstructor
public class BaseEntityValidator {

  public static final String BASE_ENTITY_FILTER_KEY = "base_entity";

  /**
   * Keys of the SQL list filters that have an indexed equivalent a widget can group by, per base
   * entity. Named in the 400 so the caller knows what to send instead.
   */
  static final Map<String, Map<String, String>> INDEXED_EQUIVALENTS =
      Map.of(
          "finding",
          Map.of(
              "finding_assets", "base_endpoint_side",
              "finding_asset_groups", "base_asset_groups_side"));

  private final EngineContext engineContext;

  /** The {@code base_entity} values the engine answers for: the index names of its models. */
  public SortedSet<String> validBaseEntities() {
    return engineContext.getModels().stream()
        .map(EsModel::getName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * The fields a structural histogram may group by, per base entity: the {@link Queryable} fields
   * of each engine model's search document, its superclasses included.
   */
  public SortedMap<String, SortedSet<String>> validGroupByFields() {
    SortedMap<String, SortedSet<String>> fields = new TreeMap<>();
    for (EsModel<?> model : engineContext.getModels()) {
      fields.put(model.getName(), queryableFields(model.getModel()));
    }
    return fields;
  }

  /** The names of the {@link Queryable} fields of a search document class and its superclasses. */
  static SortedSet<String> queryableFields(Class<?> documentClass) {
    SortedSet<String> fields = new TreeSet<>();
    for (Class<?> clazz = documentClass;
        clazz != null && clazz != Object.class;
        clazz = clazz.getSuperclass()) {
      for (Field field : clazz.getDeclaredFields()) {
        if (field.isAnnotationPresent(Queryable.class)) {
          fields.add(field.getName());
        }
      }
    }
    return fields;
  }

  /**
   * Validates the {@code base_entity} filters of a widget configuration, then the group-by field of
   * a structural histogram against the search document of each base entity it names.
   *
   * @throws BadRequestException when a value names no engine model, or the group-by field is not a
   *     field of a named entity's search document
   */
  public void validate(@Nullable WidgetConfiguration configuration) {
    validate(configuration, validBaseEntities());
    if (configuration instanceof StructuralHistogramWidget) {
      validateGroupBy(configuration, validGroupByFields());
    }
  }

  /**
   * Validates the {@code base_entity} filters of a widget configuration against a set of valid
   * values. Pure, so it is unit-tested without an engine.
   *
   * @throws BadRequestException when a value is not in {@code validValues}
   */
  public static void validate(
      @Nullable WidgetConfiguration configuration, Collection<String> validValues) {
    List<String> invalid =
        baseEntityValues(configuration).stream()
            .filter(value -> !validValues.contains(value))
            .distinct()
            .toList();
    if (!invalid.isEmpty()) {
      throw new BadRequestException(message(invalid, validValues));
    }
  }

  /**
   * Validates the group-by field of a structural histogram against the indexed fields of every base
   * entity its series name. A series with no {@code base_entity} filter is left alone: it runs
   * across every index, and no single document defines its fields. Pure, so it is unit-tested
   * without an engine.
   *
   * @param fieldsByEntity the valid group-by fields, per base entity
   * @throws BadRequestException when the field is not a field of a named entity's document
   */
  public static void validateGroupBy(
      @Nullable WidgetConfiguration configuration,
      Map<String, ? extends Collection<String>> fieldsByEntity) {
    if (!(configuration instanceof StructuralHistogramWidget structural)) {
      return;
    }
    String field = structural.getField();
    if (field == null || field.isBlank()) {
      return;
    }
    Map<String, Collection<String>> rejectedBy = new LinkedHashMap<>();
    for (String entity : new LinkedHashSet<>(baseEntityValues(configuration))) {
      Collection<String> valid = fieldsByEntity.get(entity);
      if (valid != null && !valid.contains(field)) {
        rejectedBy.put(entity, valid);
      }
    }
    if (!rejectedBy.isEmpty()) {
      throw new BadRequestException(groupByMessage(field, rejectedBy));
    }
  }

  static String groupByMessage(String field, Map<String, Collection<String>> rejectedBy) {
    StringBuilder message = new StringBuilder("Invalid group-by field '").append(field).append("'");
    for (Map.Entry<String, Collection<String>> entry : rejectedBy.entrySet()) {
      String entity = entry.getKey();
      message
          .append(": base_entity '")
          .append(entity)
          .append("' does not index it, so a histogram on it would be empty");
      String equivalent = INDEXED_EQUIVALENTS.getOrDefault(entity, Map.of()).get(field);
      if (equivalent != null && entry.getValue().contains(equivalent)) {
        message.append(". Use '").append(equivalent).append("' instead");
      }
      message
          .append(". Valid group-by fields for '")
          .append(entity)
          .append("': ")
          .append(String.join(", ", new TreeSet<>(entry.getValue())));
    }
    return message.toString();
  }

  /** Every value of every {@code base_entity} filter of the configuration, in order. */
  static List<String> baseEntityValues(@Nullable WidgetConfiguration configuration) {
    List<Filters.FilterGroup> groups = new ArrayList<>();
    if (configuration instanceof WidgetConfigurationWithSeries withSeries
        && withSeries.getSeries() != null) {
      withSeries.getSeries().stream()
          .filter(Objects::nonNull)
          .map(WidgetConfigurationWithSeries.Series::getFilter)
          .forEach(groups::add);
    } else if (configuration instanceof ListConfiguration list && list.getPerspective() != null) {
      groups.add(list.getPerspective().getFilter());
    }
    return groups.stream()
        .filter(group -> group != null && group.getFilters() != null)
        .flatMap(group -> group.getFilters().stream())
        .filter(filter -> filter != null && BASE_ENTITY_FILTER_KEY.equals(filter.getKey()))
        .filter(filter -> filter.getValues() != null)
        .flatMap(filter -> filter.getValues().stream())
        .filter(Objects::nonNull)
        .toList();
  }

  static String message(List<String> invalid, Collection<String> validValues) {
    StringBuilder message =
        new StringBuilder("Invalid base_entity value(s): ").append(String.join(", ", invalid));
    for (String value : invalid) {
      String successor = RetiredIndexes.SUCCESSORS.get(value);
      if (successor != null) {
        message
            .append(". '")
            .append(value)
            .append("' was retired and replaced by '")
            .append(successor)
            .append("'");
      }
    }
    Set<String> sorted = new LinkedHashSet<>(new TreeSet<>(validValues));
    return message.append(". Valid values: ").append(String.join(", ", sorted)).toString();
  }
}
