package io.openaev.service;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseTeamUser;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseTeamUserRepository;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExerciseTeamUserService {

  private final ExerciseTeamUserRepository exerciseTeamUserRepository;

  // -- CRUD --

  public ExerciseTeamUser createExerciseTeamUser(
      @NotNull Exercise exercise, @NotNull Team team, @NotNull User user) {
    ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
    exerciseTeamUser.setExercise(exercise);
    exerciseTeamUser.setTeam(team);
    exerciseTeamUser.setUser(user);
    return exerciseTeamUserRepository.save(exerciseTeamUser);
  }

  public void duplicateTeamUsers(
      @NotNull Exercise target,
      @NotNull List<ExerciseTeamUser> sourceTeamUsers,
      @NotNull Map<String, Team> contextualTeams) {
    List<ExerciseTeamUser> newTeamUsers =
        sourceTeamUsers.stream()
            .map(
                sourceTeamUser -> {
                  Team resolvedTeam =
                      contextualTeams.getOrDefault(
                          sourceTeamUser.getTeam().getId(), sourceTeamUser.getTeam());
                  ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
                  exerciseTeamUser.setExercise(target);
                  exerciseTeamUser.setTeam(resolvedTeam);
                  exerciseTeamUser.setUser(sourceTeamUser.getUser());
                  return exerciseTeamUser;
                })
            .toList();

    exerciseTeamUserRepository.saveAll(newTeamUsers);
  }
}
