package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Scenario;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.ScenarioTeamUserRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.scenario.ScenarioService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceUnitTest {

  @Mock private ScenarioRepository scenarioRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private ScenarioTeamUserRepository scenarioTeamUserRepository;
  @Mock private TeamService teamService;
  @Mock private InjectRepository injectRepository;
  @Mock private LessonsCategoryRepository lessonsCategoryRepository;

  @InjectMocks private ScenarioService scenarioService;

  @Nested
  class ReplaceTeams {

    @Test
    void shouldFullyRemoveDeselectedTeamAndEnableOnlyNewTeams() {
      String scenarioId = "scenario-123";

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

      Scenario scenario = new Scenario();
      scenario.setId(scenarioId);
      scenario.setTeams(new ArrayList<>(List.of(existingTeam1, existingTeam2)));

      when(scenarioRepository.findById(scenarioId)).thenReturn(Optional.of(scenario));
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
      when(scenarioTeamUserRepository.existsByScenarioIdAndTeamIdAndUserId(
              scenarioId, "team-3", "user-1"))
          .thenReturn(false);
      when(teamService.find(any())).thenReturn(List.of());

      scenarioService.replaceTeams(scenarioId, List.of("team-2", "team-3", "team-3"));

      verify(scenarioTeamUserRepository)
          .deleteByScenarioIdAndTeamIds(
              eq(scenarioId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));
      verify(injectRepository)
          .removeTeamsForScenario(
              eq(scenarioId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));
      verify(lessonsCategoryRepository)
          .removeTeamsForScenario(
              eq(scenarioId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));

      verify(scenarioTeamUserRepository)
          .existsByScenarioIdAndTeamIdAndUserId(scenarioId, "team-3", "user-1");
      verify(scenarioTeamUserRepository, never())
          .existsByScenarioIdAndTeamIdAndUserId(scenarioId, "team-2", "user-1");

      assertEquals(2, scenario.getTeams().size());
      assertTrue(scenario.getTeams().stream().anyMatch(t -> "team-2".equals(t.getId())));
      assertTrue(scenario.getTeams().stream().anyMatch(t -> "team-3".equals(t.getId())));
    }

    @Test
    void shouldNotCallCleanupWhenNoTeamIsRemoved() {
      String scenarioId = "scenario-123";

      Team existingTeam = new Team();
      existingTeam.setId("team-1");
      existingTeam.setUsers(new ArrayList<>());

      Scenario scenario = new Scenario();
      scenario.setId(scenarioId);
      scenario.setTeams(new ArrayList<>(List.of(existingTeam)));

      when(scenarioRepository.findById(scenarioId)).thenReturn(Optional.of(scenario));
      when(teamRepository.findAllById(any())).thenReturn(List.of(existingTeam));
      when(teamService.find(any())).thenReturn(List.of());

      scenarioService.replaceTeams(scenarioId, List.of("team-1"));

      verify(scenarioTeamUserRepository, never()).deleteByScenarioIdAndTeamIds(any(), any());
      verify(injectRepository, never()).removeTeamsForScenario(any(), any());
      verify(lessonsCategoryRepository, never()).removeTeamsForScenario(any(), any());
    }
  }
}
