package io.openaev.rest.exercise.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ExerciseTeamUserRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.TeamService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceUnitTest {

  @Mock private TeamService teamService;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private ExerciseTeamUserRepository exerciseTeamUserRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private LessonsCategoryRepository lessonsCategoryRepository;

  @Spy @InjectMocks private ExerciseService mockedExerciseService;

  /* ============================================================
   * replaceTeams
   * ============================================================ */
  @Nested
  class ReplaceTeams {

    @Test
    void shouldFullyRemoveDeselectedTeamAndEnableOnlyNewTeams() {
      String exerciseId = "exercise-123";

      Team existingTeam1 = new Team();
      existingTeam1.setId("team-1");
      existingTeam1.setUsers(new ArrayList<>());

      Team existingTeam2 = new Team();
      existingTeam2.setId("team-2");
      existingTeam2.setUsers(new ArrayList<>());

      User newPlayer = new User();
      newPlayer.setId("user-1");

      Team newTeam = new Team();
      newTeam.setId("team-3");
      newTeam.setUsers(List.of(newPlayer));

      Exercise exercise = new Exercise();
      exercise.setId(exerciseId);
      exercise.setTeams(new ArrayList<>(List.of(existingTeam1, existingTeam2)));

      when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));
      when(teamRepository.findAllById(any()))
          .thenAnswer(
              invocation -> {
                Iterable<String> ids = invocation.getArgument(0);
                Map<String, Team> teamsById = Map.of("team-2", existingTeam2, "team-3", newTeam);
                List<Team> result = new ArrayList<>();
                ids.forEach(
                    id -> {
                      Team team = teamsById.get(id);
                      if (team != null) {
                        result.add(team);
                      }
                    });
                return result;
              });
      when(userRepository.findById("user-1")).thenReturn(Optional.of(newPlayer));
      when(exerciseTeamUserRepository.existsByExerciseIdAndTeamIdAndUserId(
              exerciseId, "team-3", "user-1"))
          .thenReturn(false);
      when(teamService.find(any())).thenReturn(List.of());

      mockedExerciseService.replaceTeams(exerciseId, List.of("team-2", "team-3", "team-3"));

      verify(exerciseTeamUserRepository)
          .deleteByExerciseIdAndTeamIds(
              eq(exerciseId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));
      verify(injectRepository)
          .removeTeamsForExercise(
              eq(exerciseId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));
      verify(lessonsCategoryRepository)
          .removeTeamsForExercise(
              eq(exerciseId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));

      verify(exerciseTeamUserRepository)
          .existsByExerciseIdAndTeamIdAndUserId(exerciseId, "team-3", "user-1");
      verify(exerciseTeamUserRepository, never())
          .existsByExerciseIdAndTeamIdAndUserId(exerciseId, "team-2", "user-1");

      assertEquals(2, exercise.getTeams().size());
      assertTrue(exercise.getTeams().stream().anyMatch(team -> "team-2".equals(team.getId())));
      assertTrue(exercise.getTeams().stream().anyMatch(team -> "team-3".equals(team.getId())));
    }

    @Test
    void shouldNotCallCleanupWhenNoTeamIsRemoved() {
      String exerciseId = "exercise-123";

      Team existingTeam = new Team();
      existingTeam.setId("team-1");
      existingTeam.setUsers(new ArrayList<>());

      Exercise exercise = new Exercise();
      exercise.setId(exerciseId);
      exercise.setTeams(new ArrayList<>(List.of(existingTeam)));

      when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));
      when(teamRepository.findAllById(any())).thenReturn(List.of(existingTeam));
      when(teamService.find(any())).thenReturn(List.of());

      mockedExerciseService.replaceTeams(exerciseId, List.of("team-1"));

      verify(exerciseTeamUserRepository, never()).deleteByExerciseIdAndTeamIds(any(), any());
      verify(injectRepository, never()).removeTeamsForExercise(any(), any());
      verify(lessonsCategoryRepository, never()).removeTeamsForExercise(any(), any());
    }
  }
}
