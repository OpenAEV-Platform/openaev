package io.openaev.rest.custom_dashboard.utils;

import io.openaev.database.model.Filters;
import io.openaev.engine.api.HistogramInterval;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public final class WidgetUtils {

  private WidgetUtils() {}

  public static List<String> getColumnsFromBaseEntityName(String entityName) {
    return switch (entityName) {
      case "endpoint" -> List.of("endpoint_name", "endpoint_ips", "endpoint_platform");
      case "vulnerable-endpoint" ->
          List.of(
              "vulnerable_endpoint_hostname",
              "vulnerable_endpoint_action",
              "vulnerable_endpoint_findings_summary");
      case "expectation-inject" ->
          List.of(
              "inject_title",
              "inject_expectation_type",
              "inject_expectation_status",
              "inject_expectation_source");
      case "finding" -> List.of("finding_value", "finding_type", "base_updated_at");
      case "inject" -> List.of("inject_title", "base_attack_patterns_side", "execution_date");
      case "simulation" -> List.of("name", "base_updated_at", "base_tags_side");
      case "scenario" -> List.of("name", "base_updated_at", "base_tags_side");
      default -> List.of("id");
    };
  }

  public static String getBaseEntityFilterValue(Filters.FilterGroup filter) {
    return filter.getFilters().stream()
        .filter(f -> f.getKey().equals("base_entity"))
        .findFirst()
        .map(Filters.Filter::getValues)
        .flatMap(values -> values.stream().findFirst())
        .orElse(null);
  }

  public static void setOrAddFilterByKey(
      Filters.FilterGroup filterGroup,
      String key,
      List<String> values,
      Filters.FilterOperator operator) {
    Optional<Filters.Filter> existingFilter =
        filterGroup.getFilters().stream().filter(f -> f.getKey().equals(key)).findFirst();

    if (existingFilter.isPresent()) {
      existingFilter.get().setValues(values);
      existingFilter.get().setOperator(operator);
      existingFilter.get().setMode(Filters.FilterMode.or);
    } else {
      Filters.Filter newFilter = new Filters.Filter();
      newFilter.setKey(key);
      newFilter.setOperator(operator);
      newFilter.setMode(Filters.FilterMode.or);
      newFilter.setValues(values);
      filterGroup.getFilters().add(newFilter);
    }
  }

  public static String calcEndDate(String startDate, HistogramInterval interval) {
    OffsetDateTime date = OffsetDateTime.parse(startDate);
    OffsetDateTime endDate =
        switch (interval) {
          case hour -> date.plusHours(1);
          case day -> date.plusDays(1);
          case week -> date.plusDays(7);
          case month -> date.plusMonths(1);
          case quarter -> date.plusMonths(3);
          case year -> date.plusYears(1);
        };
    return endDate.format(DateTimeFormatter.ISO_INSTANT);
  }
}
