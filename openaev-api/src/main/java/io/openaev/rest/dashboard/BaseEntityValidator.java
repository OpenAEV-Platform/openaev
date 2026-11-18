package io.openaev.rest.dashboard;

import io.openaev.database.model.Filters;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.RetiredIndexes;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.WidgetConfiguration;
import io.openaev.engine.api.WidgetConfigurationWithSeries;
import io.openaev.rest.exception.BadRequestException;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Rejects a widget whose {@code base_entity} filter names no engine model.
 *
 * <p>Every widget query runs against the {@code <prefix>_*} index pattern, so a {@code base_entity}
 * value that matches no model (a typo, or a retired name such as {@code endpoint}) does not fail:
 * it simply matches no document, and the widget answers 0. A caller reading "0 assets" cannot tell
 * that from an empty inventory, so the value is refused with HTTP 400 instead, naming it, the
 * successor of a retired name and the valid values.
 */
@Component
@RequiredArgsConstructor
public class BaseEntityValidator {

  public static final String BASE_ENTITY_FILTER_KEY = "base_entity";

  private final EngineContext engineContext;

  /** The {@code base_entity} values the engine answers for: the index names of its models. */
  public SortedSet<String> validBaseEntities() {
    return engineContext.getModels().stream()
        .map(EsModel::getName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * Validates the {@code base_entity} filters of a widget configuration.
   *
   * @throws BadRequestException when a value names no engine model
   */
  public void validate(@Nullable WidgetConfiguration configuration) {
    validate(configuration, validBaseEntities());
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
