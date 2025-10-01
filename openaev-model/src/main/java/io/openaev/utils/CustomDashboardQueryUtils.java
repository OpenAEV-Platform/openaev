package io.openaev.utils;

import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.*;
import static io.openaev.utils.CustomDashboardTimeRange.*;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.CustomDashboardParameters;
import io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType;
import io.openaev.engine.api.WidgetConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public class CustomDashboardQueryUtils {

  private CustomDashboardQueryUtils() {}

  public static boolean isAllTime(
      WidgetConfiguration widgetConfig,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters) {
    if (ALL_TIME.equals(widgetConfig.getTimeRange())) {
      return true;
    }
    if (DEFAULT.equals(widgetConfig.getTimeRange())) {
      final String timeRangeParameterId = findParamIdByType(definitionParameters, timeRange);
      CustomDashboardTimeRange dashboardTimeRange = fromValue(parameters.get(timeRangeParameterId));
      return ALL_TIME.equals(dashboardTimeRange);
    }
    return false;
  }

  public static Instant calcStartDate(
      WidgetConfiguration widgetConfig,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters) {
    final String timeRangeParameterId = findParamIdByType(definitionParameters, timeRange);
    final String startDateParameterId = findParamIdByType(definitionParameters, startDate);

    CustomDashboardTimeRange widgetTimeRange = fromValue(widgetConfig.getTimeRange().name());
    final Instant now = Instant.now();

    switch (widgetTimeRange) {
      case LAST_DAY:
        return now.minus(24, ChronoUnit.HOURS);
      case LAST_WEEK:
        return now.minus(7, ChronoUnit.DAYS);
      case LAST_MONTH:
        return now.minus(30, ChronoUnit.DAYS);
      case LAST_QUARTER:
        return now.minus(90, ChronoUnit.DAYS);
      case LAST_SEMESTER:
        return now.minus(180, ChronoUnit.DAYS);
      case LAST_YEAR:
        return now.minus(360, ChronoUnit.DAYS);
      case CUSTOM:
        if (hasText(widgetConfig.getStart())) {
          return Instant.parse(
              parameters.getOrDefault(widgetConfig.getStart(), widgetConfig.getStart()));
        }
      case DEFAULT:
        CustomDashboardTimeRange dashboardTimeRange =
            fromValue(parameters.get(timeRangeParameterId));
        switch (dashboardTimeRange) {
          case LAST_DAY:
            return now.minus(24, ChronoUnit.HOURS);
          case LAST_WEEK:
            return now.minus(7, ChronoUnit.DAYS);
          case LAST_MONTH:
            return now.minus(30, ChronoUnit.DAYS);
          case LAST_QUARTER:
            return now.minus(90, ChronoUnit.DAYS);
          case LAST_SEMESTER:
            return now.minus(180, ChronoUnit.DAYS);
          case LAST_YEAR:
            return now.minus(360, ChronoUnit.DAYS);
          case CUSTOM:
            if (parameters.get(startDateParameterId) != null) {
              return Instant.parse(parameters.get(startDateParameterId));
            }
          default:
        }
      default:
    }
    return now;
  }

  public static Instant calcEndDate(
      WidgetConfiguration widgetConfig,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters) {

    final String timeRangeParameterId = findParamIdByType(definitionParameters, timeRange);
    final String endDateParameterId = findParamIdByType(definitionParameters, endDate);

    CustomDashboardTimeRange widgetTimeRange = fromValue(widgetConfig.getTimeRange().name());
    final Instant now = Instant.now();

    switch (widgetTimeRange) {
      case CUSTOM:
        if (hasText(widgetConfig.getEnd())) {
          return Instant.parse(
              parameters.getOrDefault(widgetConfig.getEnd(), widgetConfig.getEnd()));
        }
      case DEFAULT:
        CustomDashboardTimeRange dashboardTimeRange =
            fromValue(parameters.get(timeRangeParameterId));
        if (dashboardTimeRange.equals(CUSTOM)) {
          if (parameters.get(endDateParameterId) != null) {
            return Instant.parse(parameters.get(endDateParameterId));
          }
        } else {
          return now;
        }
      default:
        return now;
    }
  }

  // -- PRIVATE --

  private static String findParamIdByType(
      Map<String, CustomDashboardParameters> definitionParameters,
      CustomDashboardParameterType type) {
    return definitionParameters.entrySet().stream()
        .filter(entry -> type.equals(entry.getValue().getType()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing parameter with type '" + type + "'"));
  }
}
