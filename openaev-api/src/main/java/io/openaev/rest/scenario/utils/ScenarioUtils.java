package io.openaev.rest.scenario.utils;

import static io.openaev.utils.CustomFilterUtils.computeMode;
import static java.util.Optional.ofNullable;

import io.openaev.database.model.Filters;
import io.openaev.database.model.Scenario;
import io.openaev.database.specification.ScenarioSpecification;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class ScenarioUtils {

  private ScenarioUtils() {}

  private static final String SCENARIO_RECURRENCE_FILTER = "scenario_recurrence";

  /** Manage filters that are not directly managed by the generic mechanics */
  public static UnaryOperator<Specification<Scenario>> handleCustomFilter(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    // Existence of the filter
    Optional<Filters.Filter> scenarioRecurrenceFilterOpt =
        ofNullable(searchPaginationInput.getFilterGroup())
            .flatMap(f -> f.findByKey(SCENARIO_RECURRENCE_FILTER));

    if (scenarioRecurrenceFilterOpt.isPresent()) {
      // Purge filter
      searchPaginationInput.getFilterGroup().removeByKey(SCENARIO_RECURRENCE_FILTER);
      Specification<Scenario> customSpecification = null;
      if (scenarioRecurrenceFilterOpt.get().getValues().contains("Scheduled")) {
        customSpecification = ScenarioSpecification.isRecurring();
      } else if (scenarioRecurrenceFilterOpt.get().getValues().contains("Not planned")) {
        customSpecification = ScenarioSpecification.noRecurring();
      }
      if (customSpecification != null) {
        return computeMode(searchPaginationInput, customSpecification);
      }
      return (Specification<Scenario> specification) -> specification;
    } else {
      return (Specification<Scenario> specification) -> specification;
    }
  }
}
