package io.openaev.rest.scenario;

import io.openaev.database.model.Scenario;
import io.openaev.rest.kill_chain_phase.KillChainPhaseInitializer;
import org.hibernate.Hibernate;

/**
 * Hydrates, inside the scoped transaction, the lazy associations a scenario response serializes.
 * They are read after the controller returns, through open-in-view, where the tenant scope is
 * already gone: a lazy load of an isolated table at that point reads zero rows, so {@code
 * scenario_teams}, {@code scenario_tags} and {@code scenario_documents} would come back empty.
 * {@code scenario_kill_chain_phases} walks the injects down to the lazy attack-pattern phases, see
 * {@link KillChainPhaseInitializer}.
 */
public final class ScenarioResponseHydrator {

  private ScenarioResponseHydrator() {}

  /** A lazy load after the commit reads zero rows, so the response would carry an empty array. */
  public static Scenario hydrateForResponse(Scenario scenario) {
    KillChainPhaseInitializer.initializeFromInjects(scenario.getInjects());
    Hibernate.initialize(scenario.getTeams());
    Hibernate.initialize(scenario.getTags());
    Hibernate.initialize(scenario.getDocuments());
    return scenario;
  }
}
