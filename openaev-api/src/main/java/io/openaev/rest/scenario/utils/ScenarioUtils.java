package io.openaev.rest.scenario.utils;

import static io.openaev.utils.CustomFilterUtils.computeMode;
import static java.util.Optional.ofNullable;

import io.openaev.database.model.Filters;
import io.openaev.database.model.Scenario;
import io.openaev.database.specification.ScenarioSpecification;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.data.jpa.domain.Specification;

public class ScenarioUtils {

  private ScenarioUtils() {}

  private static final String SCENARIO_RECURRENCE_FILTER = "scenario_recurrence";
  private static final String SCENARIO_TYPE_FILTER = "scenario_type";

  // Engine-type filter values (kept human-readable, mirroring the recurrence filter). They are the
  // exact ids the launch UI stores and the frontend ScenarioTypeFilter offers.
  public static final String SCENARIO_TYPE_TIME_BASED = "Time-based";
  public static final String SCENARIO_TYPE_CHAINED = "Chained";
  public static final String SCENARIO_TYPE_AUTONOMOUS = "Autonomous";

  /**
   * Manage filters that are not directly managed by the generic mechanics. Two composite scenario
   * facets are handled here because they cannot map to a single JPA column: {@code
   * scenario_recurrence} (scheduled vs. not) and {@code scenario_type} (time-based / chained /
   * autonomous, derived from the autonomous flag and the presence of a chaining workflow template).
   * Each matching filter is stripped from the generic group and re-expressed as a Specification;
   * the results are combined with the group's AND/OR mode.
   */
  public static UnaryOperator<Specification<Scenario>> handleCustomFilter(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    Specification<Scenario> customSpecification =
        combine(
            recurrenceSpecification(searchPaginationInput),
            typeSpecification(searchPaginationInput),
            searchPaginationInput);
    if (customSpecification == null) {
      return (Specification<Scenario> specification) -> specification;
    }
    return computeMode(searchPaginationInput, customSpecification);
  }

  /** Strips and maps the {@code scenario_recurrence} filter to a Specification (or null). */
  private static Specification<Scenario> recurrenceSpecification(
      final SearchPaginationInput searchPaginationInput) {
    Optional<Filters.Filter> filterOpt =
        ofNullable(searchPaginationInput.getFilterGroup())
            .flatMap(f -> f.findByKey(SCENARIO_RECURRENCE_FILTER));
    if (filterOpt.isEmpty()) {
      return null;
    }
    searchPaginationInput.getFilterGroup().removeByKey(SCENARIO_RECURRENCE_FILTER);
    List<String> values = filterOpt.get().getValues();
    if (values == null) {
      return null;
    }
    if (values.contains("Scheduled")) {
      return ScenarioSpecification.isRecurring();
    }
    if (values.contains("Not planned")) {
      return ScenarioSpecification.noRecurring();
    }
    return null;
  }

  /**
   * Strips and maps the {@code scenario_type} filter to a Specification (or null). Multiple
   * selected types are OR-combined (a scenario matches if it is any of the picked kinds).
   */
  private static Specification<Scenario> typeSpecification(
      final SearchPaginationInput searchPaginationInput) {
    Optional<Filters.Filter> filterOpt =
        ofNullable(searchPaginationInput.getFilterGroup())
            .flatMap(f -> f.findByKey(SCENARIO_TYPE_FILTER));
    if (filterOpt.isEmpty()) {
      return null;
    }
    searchPaginationInput.getFilterGroup().removeByKey(SCENARIO_TYPE_FILTER);
    List<String> values = filterOpt.get().getValues();
    if (values == null || values.isEmpty()) {
      return null;
    }
    Specification<Scenario> spec = null;
    for (String value : values) {
      Specification<Scenario> valueSpec =
          switch (value) {
            case SCENARIO_TYPE_AUTONOMOUS -> ScenarioSpecification.isAutonomous();
            case SCENARIO_TYPE_CHAINED -> ScenarioSpecification.isChained();
            case SCENARIO_TYPE_TIME_BASED -> ScenarioSpecification.isTimeBased();
            default -> null;
          };
      if (valueSpec != null) {
        spec = (spec == null) ? valueSpec : spec.or(valueSpec);
      }
    }
    return spec;
  }

  /**
   * Combines two custom specifications with the filter group's AND/OR mode (nulls pass through).
   */
  private static Specification<Scenario> combine(
      final Specification<Scenario> left,
      final Specification<Scenario> right,
      final SearchPaginationInput searchPaginationInput) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    boolean orMode =
        searchPaginationInput.getFilterGroup() != null
            && Filters.FilterMode.or.equals(searchPaginationInput.getFilterGroup().getMode());
    return orMode ? left.or(right) : left.and(right);
  }
}
