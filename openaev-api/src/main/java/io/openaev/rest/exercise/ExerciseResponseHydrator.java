package io.openaev.rest.exercise;

import io.openaev.database.model.Exercise;
import io.openaev.rest.kill_chain_phase.KillChainPhaseInitializer;
import org.hibernate.Hibernate;

/**
 * Hydrates, inside the scoped transaction, the lazy associations a simulation response serializes.
 * They are read after the controller returns, through open-in-view, where the tenant scope is
 * already gone: a lazy load at that point runs unscoped and fails closed, so {@code exercise_teams}
 * comes back empty and an inject's document is reported missing. {@code exercise_kill_chain_phases}
 * walks the injects down to the lazy attack-pattern phases, see {@link KillChainPhaseInitializer}.
 */
public final class ExerciseResponseHydrator {

  private ExerciseResponseHydrator() {}

  public static Exercise hydrateForResponse(Exercise exercise) {
    KillChainPhaseInitializer.initializeFromInjects(exercise.getInjects());
    Hibernate.initialize(exercise.getTeams());
    return exercise;
  }
}
