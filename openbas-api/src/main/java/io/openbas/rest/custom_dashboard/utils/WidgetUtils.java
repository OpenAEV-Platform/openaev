package io.openbas.rest.custom_dashboard.utils;

import io.openbas.database.model.Filters;
import io.openbas.engine.api.HistogramInterval;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class WidgetUtils {

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
              "inject_expectation_type",
              "inject_expectation_status",
              "inject_title",
              "inject_expectation_source");
      case "finding" -> List.of("finding_type", "base_updated_at", "finding_value");
      case "inject" ->
          List.of("inject_title", "base_attack_patterns_side", "inject_execution_date");
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
    OffsetDateTime endDate;

    switch (interval) {
      case HistogramInterval.day:
        endDate = date.plusDays(1);
        break;
      case HistogramInterval.week:
        endDate = date.plusDays(7);
        break;
      case HistogramInterval.month:
        endDate = date.plusMonths(1);
        break;
      case HistogramInterval.quarter:
        endDate = date.plusMonths(3);
        break;
      case HistogramInterval.year:
        endDate = date.plusYears(1);
        break;
      default:
        return null;
    }

    return endDate.format(DateTimeFormatter.ISO_INSTANT);
  }
}
