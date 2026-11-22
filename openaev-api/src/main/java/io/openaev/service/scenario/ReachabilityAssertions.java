package io.openaev.service.scenario;

import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;

/**
 * Rules of a reachability assertion scenario ({@code scenario_reachability_assertion}, opt-in).
 *
 * <p>A reachability assertion checks, on the scenario's recurrence, that its targets still answer
 * as declared by the expectations of its injects. It may only read its targets: every enabled
 * inject must have an injector contract that is read-only, by itself or through its payload (the
 * read-only marker of injector contracts and payloads). A scenario that is not an assertion is
 * never constrained by these rules.
 */
public final class ReachabilityAssertions {

  private ReachabilityAssertions() {}

  /** True when the inject only reads its target: its contract or its payload is read-only. */
  public static boolean isReadOnly(@NotNull Inject inject) {
    return inject.getInjectorContract().map(InjectorContract::isReadOnlyEffective).orElse(false);
  }

  /** The enabled injects that are not read-only (empty when the assertion may run). */
  public static List<Inject> nonReadOnlyInjects(@NotNull Collection<Inject> injects) {
    return injects.stream()
        .filter(Inject::isEnabled)
        .filter(inject -> !isReadOnly(inject))
        .toList();
  }

  /**
   * True when the scenario may run on its schedule: it is not an assertion, or every enabled inject
   * of the assertion is read-only.
   */
  public static boolean mayRun(@NotNull Scenario scenario) {
    return !scenario.isReachabilityAssertion()
        || nonReadOnlyInjects(scenario.getInjects()).isEmpty();
  }

  /**
   * The refusal message for an assertion holding injects that are not read-only, or {@code null}
   * when it may run.
   */
  public static String refusal(@NotNull Scenario scenario) {
    if (mayRun(scenario)) {
      return null;
    }
    List<String> names =
        nonReadOnlyInjects(scenario.getInjects()).stream().map(Inject::getTitle).toList();
    return "A reachability assertion runs read-only injects only; not read-only: "
        + String.join(", ", names);
  }
}
