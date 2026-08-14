package io.openaev.service;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseTeamUser;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ExerciseTeamUserRepository;
import io.openaev.database.repository.TeamRepository;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ExerciseTeamUserService {

  private final ExerciseTeamUserRepository exerciseTeamUserRepository;
  private final ExerciseRepository exerciseRepository;
  private final TeamRepository teamRepository;

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

  /**
   * Enables, on the given simulation, the members of every targeted team so a human-in-the-loop
   * inject (email, SMS, credential harvesting, ...) can actually reach them. The email/SMS executor
   * resolves recipients from {@code exercise_teams_users} (players ENABLED on the simulation), not
   * from raw team membership, so targeting a team whose members were never enabled fails with
   * "Email needs at least one user". This makes any team-targeted step deliverable regardless of
   * how the team was wired (a scope team chosen in the chaining Configure-action drawer, an
   * operator pre-selected audience, a wrapper the orchestrator built, or {@code
   * ensure_openaev_target_team}).
   *
   * <p>Lives here (repository-only dependencies) rather than on {@code ExerciseService} so the
   * chaining {@code InjectExecutionStep} can reuse it without forming a Spring bean cycle ({@code
   * InjectExecutionStep -> ExerciseService -> StepService -> InjectExecutionStep}). Best-effort and
   * idempotent: already-enabled links are skipped, and a team-less (asset-only) step targets
   * nothing here.
   *
   * @param simulationId the simulation whose audience must carry the targeted teams' players
   * @param teamIds the ids of the teams targeted by a chained/authored step
   */
  public void enableTargetedTeamMembers(String simulationId, List<String> teamIds) {
    enableTargetedTeamMembers(simulationId, teamIds, Set.of());
  }

  /**
   * Same as {@link #enableTargetedTeamMembers(String, List)} but never enables the given user ids.
   * A player denylisted in a chaining workflow scope must not join the simulation audience at all:
   * filtering them from the inject's recipients is not enough, because the {@code
   * exercise_teams_users} link created here persists beyond the inject.
   *
   * @param simulationId the simulation whose audience must carry the targeted teams' players
   * @param teamIds the ids of the teams targeted by a chained/authored step
   * @param excludedUserIds user ids that must never be enabled (e.g. scope-denylisted players)
   */
  public void enableTargetedTeamMembers(
      String simulationId, List<String> teamIds, Set<String> excludedUserIds) {
    if (teamIds == null || teamIds.isEmpty() || !StringUtils.hasText(simulationId)) {
      return;
    }
    Exercise simulation = exerciseRepository.findById(simulationId).orElse(null);
    if (simulation == null) {
      return;
    }
    Set<String> excluded = excludedUserIds != null ? excludedUserIds : Set.of();
    for (String teamId : teamIds.stream().filter(StringUtils::hasText).distinct().toList()) {
      Team team = teamRepository.findById(teamId).orElse(null);
      if (team == null) {
        continue;
      }
      List<User> members =
          team.getUsers().stream().distinct().filter(m -> !excluded.contains(m.getId())).toList();
      if (members.isEmpty()) {
        continue;
      }
      boolean onSimulation = simulation.getTeams().stream().anyMatch(t -> teamId.equals(t.getId()));
      if (!onSimulation) {
        team.getExercises().add(simulation);
        teamRepository.save(team);
      }
      for (User member : members) {
        boolean alreadyLinked =
            exerciseTeamUserRepository.existsByExerciseIdAndTeamIdAndUserId(
                simulationId, teamId, member.getId());
        if (!alreadyLinked) {
          createExerciseTeamUser(simulation, team, member);
        }
      }
    }
  }
}
