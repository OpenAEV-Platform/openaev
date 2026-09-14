package io.openaev.utils;

import io.openaev.rest.team.output.TeamOutput;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TeamOutputVisibilityUtils {

  private TeamOutputVisibilityUtils() {}

  public static List<TeamOutput> markScenarioVisibility(List<TeamOutput> teams, String scenarioId) {
    return teams.stream().map(team -> markScenarioVisibility(team, scenarioId)).toList();
  }

  public static List<TeamOutput> markExerciseVisibility(List<TeamOutput> teams, String exerciseId) {
    return teams.stream().map(team -> markExerciseVisibility(team, exerciseId)).toList();
  }

  private static TeamOutput markScenarioVisibility(final TeamOutput team, final String scenarioId) {
    Set<String> scenarios =
        new HashSet<>(team.getScenarios() == null ? Set.of() : team.getScenarios());
    scenarios.add(scenarioId);
    team.setScenarios(scenarios);
    return team;
  }

  private static TeamOutput markExerciseVisibility(final TeamOutput team, final String exerciseId) {
    Set<String> exercises =
        new HashSet<>(team.getExercises() == null ? Set.of() : team.getExercises());
    exercises.add(exerciseId);
    team.setExercises(exercises);
    return team;
  }
}
