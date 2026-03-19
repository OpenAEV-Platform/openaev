package io.openaev.utils.fixtures;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseTeamUser;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;

public class ExerciseTeamUserFixture {

  public static ExerciseTeamUser createExerciseTeamUser(Exercise exercise, Team team, User user) {
    ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
    exerciseTeamUser.setExercise(exercise);
    exerciseTeamUser.setTeam(team);
    exerciseTeamUser.setUser(user);
    return exerciseTeamUser;
  }
}
