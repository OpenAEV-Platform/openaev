package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.ScenarioTeamUserRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TargetType;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import java.util.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceUnitTest {

  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private TeamService teamService;
  @Mock private InjectService injectService;
  @Mock private TagRuleService tagRuleService;
  @Mock private ScenarioRepository scenarioRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private ScenarioTeamUserRepository scenarioTeamUserRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private LessonsCategoryRepository lessonsCategoryRepository;

  @InjectMocks private ScenarioService scenarioService;

  @Test
  public void testUpdateScenario_WITH_applyRule_true() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = new Inject();
    inject1.setId("1");
    Inject inject2 = new Inject();
    inject1.setId("2");
    Scenario scenario = ScenarioFixture.getScenario(null, Set.of(inject1, inject2));
    scenario.setTags(Set.of(tag1, tag2));
    Set<Tag> currentTags = Set.of(tag2, tag3);
    List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

    when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
        .thenReturn(assetGroupsToAdd);
    when(scenarioRepository.save(scenario)).thenReturn(scenario);
    when(injectService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS))).thenReturn(true);

    scenarioService.updateScenario(scenario, currentTags, true);

    scenario
        .getInjects()
        .forEach(
            inject ->
                verify(injectService)
                    .applyDefaultAssetGroupsToInject(inject.getId(), assetGroupsToAdd));
    verify(scenarioRepository).save(scenario);
  }

  @Test
  public void testUpdateScenario_WITH_applyRule_true_and_manual_inject() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = new Inject();
    inject1.setId("1");
    Inject inject2 = new Inject();
    inject1.setId("2");
    Scenario scenario = ScenarioFixture.getScenario(null, Set.of(inject1, inject2));
    scenario.setTags(Set.of(tag1, tag2));
    Set<Tag> currentTags = Set.of(tag2, tag3);
    List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

    when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
        .thenReturn(assetGroupsToAdd);
    when(scenarioRepository.save(scenario)).thenReturn(scenario);
    when(injectService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS))).thenReturn(false);

    scenarioService.updateScenario(scenario, currentTags, true);

    verify(injectService, never()).applyDefaultAssetGroupsToInject(any(), any());
    verify(scenarioRepository).save(scenario);
  }

  @Test
  public void testUpdateScenario_WITH_applyRule_false() {
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = new Inject();
    inject1.setId("1");
    Inject inject2 = new Inject();
    inject2.setId("2");
    Scenario scenario = ScenarioFixture.getScenario(null, Set.of(inject1, inject2));
    scenario.setTags(Set.of(tag1, tag2));
    Set<Tag> currentTags = Set.of(tag2, tag3);

    when(scenarioRepository.save(scenario)).thenReturn(scenario);

    scenarioService.updateScenario(scenario, currentTags, false);

    verify(injectService, never()).applyDefaultAssetGroupsToInject(any(), any());
    verify(scenarioRepository).save(scenario);
  }

  private AssetGroup getAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(name);
    assetGroup.setId(name);
    return assetGroup;
  }

  @Nested
  class CreateScenario {

    @Test
    void shouldSaveScenario_andKeepExistingFrom() {
      // -------- Prepare --------
      Scenario scenario = ScenarioFixture.getScenario();
      when(scenarioRepository.save(any(Scenario.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Scenario result = scenarioService.createScenario(scenario);

      // -------- Assert --------
      assertNotNull(result);
      // ScenarioFixture sets from to "simulation@mail.fr" so it should be preserved
      assertEquals("simulation@mail.fr", result.getFrom());
    }

    @Test
    void shouldReturnSavedScenario() {
      // -------- Prepare --------
      Scenario scenario = ScenarioFixture.getScenario();
      Scenario saved = ScenarioFixture.getScenario();
      saved.setId("saved-id");
      when(scenarioRepository.save(any(Scenario.class))).thenReturn(saved);

      // -------- Act --------
      Scenario result = scenarioService.createScenario(scenario);

      // -------- Assert --------
      assertNotNull(result);
      assertEquals("saved-id", result.getId());
    }
  }

  @Nested
  class ComputeEmails {

    @Test
    void shouldKeepExistingFrom_whenAlreadySet() {
      // -------- Prepare --------
      Scenario scenario = new Scenario();
      scenario.setFrom("existing@mail.com");

      // -------- Act --------
      scenarioService.computeEmails(scenario);

      // -------- Assert --------
      assertEquals("existing@mail.com", scenario.getFrom());
    }
  }

  @Nested
  class RetrieveScenario {

    @Test
    void shouldReturnScenario_whenFound() {
      // -------- Prepare --------
      Scenario scenario = new Scenario();
      scenario.setId("sc-1");
      when(scenarioRepository.findById("sc-1")).thenReturn(Optional.of(scenario));

      // -------- Act --------
      Scenario result = scenarioService.scenario("sc-1");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals("sc-1", result.getId());
    }

    @Test
    void shouldThrowElementNotFoundException_whenNotFound() {
      // -------- Prepare --------
      when(scenarioRepository.findById("missing")).thenReturn(Optional.empty());

      // -------- Act / Assert --------
      assertThrows(
          io.openaev.rest.exception.ElementNotFoundException.class,
          () -> scenarioService.scenario("missing"));
    }

    @Test
    void shouldDeleteScenarioById() {
      // -------- Act --------
      scenarioService.deleteScenario("sc-1");

      // -------- Assert --------
      verify(scenarioRepository).deleteById("sc-1");
    }
  }

  @Nested
  class RecurringScenarios {

    @Test
    void shouldReturnRecurringScenarios_afterInstant() {
      // -------- Prepare --------
      Scenario scenario = new Scenario();
      when(scenarioRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
          .thenReturn(List.of(scenario));

      // -------- Act --------
      List<Scenario> result = scenarioService.recurringScenarios(java.time.Instant.now());

      // -------- Assert --------
      assertEquals(1, result.size());
    }

    @Test
    void shouldReturnPotentiallyOutdatedScenarios() {
      // -------- Prepare --------
      when(scenarioRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
          .thenReturn(Collections.emptyList());

      // -------- Act --------
      List<Scenario> result =
          scenarioService.potentialOutdatedRecurringScenario(java.time.Instant.now());

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class TeamManagement {

    @Test
    void shouldDisablePlayers() {
      // -------- Prepare --------
      Scenario scenario = new Scenario();
      scenario.setId("sc-1");
      scenario.setInjects(new HashSet<>());
      when(scenarioRepository.findById("sc-1")).thenReturn(Optional.of(scenario));

      // -------- Act --------
      Scenario result = scenarioService.disablePlayers("sc-1", "team-id", List.of("player-1"));

      // -------- Assert --------
      assertNotNull(result);
      assertEquals("sc-1", result.getId());
    }
  }

  @Nested
  class TagRules {

    @Test
    void shouldReturnTrue_whenNewTagsAdded() {
      // -------- Prepare --------
      Tag existingTag = TagFixture.getTag("Existing");
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setTags(Set.of(existingTag));
      when(tagRuleService.checkIfRulesApply(any(), any())).thenReturn(true);

      // -------- Act --------
      boolean result = scenarioService.checkIfTagRulesApplies(scenario, List.of("new-tag-id"));

      // -------- Assert --------
      assertTrue(result);
    }

    @Test
    void shouldReturnFalse_whenNoNewTags() {
      // -------- Prepare --------
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setTags(Set.of());
      when(tagRuleService.checkIfRulesApply(any(), any())).thenReturn(false);

      // -------- Act --------
      boolean result = scenarioService.checkIfTagRulesApplies(scenario, List.of());

      // -------- Assert --------
      assertFalse(result);
    }
  }

  @Nested
  class LaunchValidation {

    @Test
    void shouldNotThrow_whenLicenseActive() {
      // -------- Prepare --------
      when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
      Scenario scenario = new Scenario();
      scenario.setInjects(new HashSet<>());

      // -------- Act / Assert --------
      assertDoesNotThrow(() -> scenarioService.throwIfScenarioNotLaunchable(scenario));
    }

    @Test
    void shouldDelegateToInjectService_whenLicenseNotActive() {
      // -------- Prepare --------
      when(enterpriseEditionService.isLicenseActive(any())).thenReturn(false);
      Inject inject = new Inject();
      Scenario scenario = new Scenario();
      scenario.setInjects(new HashSet<>(List.of(inject)));

      // -------- Act --------
      scenarioService.throwIfScenarioNotLaunchable(scenario);

      // -------- Assert --------
      verify(injectService).throwIfInjectNotLaunchable(inject);
    }
  }

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
