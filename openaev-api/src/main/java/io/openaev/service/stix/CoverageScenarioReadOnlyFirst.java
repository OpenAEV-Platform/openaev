package io.openaev.service.stix;

import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The opt-in "read-only first" build of a scenario received from an OpenCTI security coverage
 * (tenant setting {@code coverage_scenario_read_only_first}, off by default).
 *
 * <p>When the setting is on and the scenario has not been started by a user yet, every inject whose
 * injector contract and payload are not marked read-only is disabled, and, when there is at least
 * one such inject, the schedule set from the coverage is cleared so nothing runs on its own. A user
 * with the launch right starts it through the existing endpoints: the injects with {@code
 * inject_enabled}, the schedule with {@code PUT /api/scenarios/{scenarioId}/recurrence}, which
 * checks the launch right and records {@code scenario_scheduled_by}. A scenario with only read-only
 * injects keeps its schedule.
 *
 * <p>Once a user scheduled the scenario ({@code scenario_scheduled_by} set), later coverage updates
 * leave its injects and schedule as the user left them.
 */
public final class CoverageScenarioReadOnlyFirst {

  private CoverageScenarioReadOnlyFirst() {}

  /** True when the inject only reads its targets: its contract or its payload is read-only. */
  public static boolean isReadOnly(@NotNull Inject inject) {
    return inject.getInjectorContract().map(InjectorContract::isReadOnlyEffective).orElse(false);
  }

  /**
   * Applies the read-only first build to the scenario and its injects.
   *
   * @return the injects disabled because they are not read-only (empty when nothing was held)
   */
  public static List<Inject> apply(
      @NotNull Scenario scenario, @NotNull Collection<Inject> injects) {
    List<Inject> held = new ArrayList<>();
    if (scenario.getScheduledBy() != null) {
      return held;
    }
    for (Inject inject : injects) {
      if (!isReadOnly(inject)) {
        inject.setEnabled(false);
        held.add(inject);
      }
    }
    if (!held.isEmpty()) {
      scenario.setRecurrence(null);
      scenario.setRecurrenceStart(null);
      scenario.setRecurrenceEnd(null);
    }
    return held;
  }
}
