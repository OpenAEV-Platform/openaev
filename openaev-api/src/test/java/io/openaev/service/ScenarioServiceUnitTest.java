package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.TargetType;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.mapper.ExerciseMapper;
import io.openaev.utils.mapper.ScenarioMapper;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScenarioService Unit Tests")
class ScenarioServiceUnitTest {

  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private VariableService variableService;
  @Mock private ChallengeService challengeService;
  @Mock private TeamService teamService;
  @Mock private FileService fileService;
  @Mock private InjectDuplicateService injectDuplicateService;
  @Mock private InjectService injectService;
  @Mock private TagRuleService tagRuleService;
  @Mock private UserService userService;
  @Mock private ScenarioMapper scenarioMapper;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private ExerciseMapper exerciseMapper;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Mock private ScenarioRepository scenarioRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private DocumentRepository documentRepository;
  @Mock private ScenarioTeamUserRepository scenarioTeamUserRepository;
  @Mock private ArticleRepository articleRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private LessonsCategoryRepository lessonsCategoryRepository;
  @Mock private HealthCheckUtils healthCheckUtils;
  @InjectMocks private ScenarioService scenarioService;

  @Nested
  @DisplayName("Update scenario")
  class UpdateScenario {

    @Test
    @DisplayName("Given applyRule true, should apply asset groups to injects")
    void given_applyRuleTrue_should_applyAssetGroupsToInjects() {
      // Arrange
      AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
      AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
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
      List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

      when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
          .thenReturn(assetGroupsToAdd);
      when(scenarioRepository.save(scenario)).thenReturn(scenario);
      when(injectService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS))).thenReturn(true);

      // Act
      scenarioService.updateScenario(scenario, currentTags, true);

      // Assert
      scenario
          .getInjects()
          .forEach(
              inject ->
                  verify(injectService)
                      .applyDefaultAssetGroupsToInject(inject.getId(), assetGroupsToAdd));
      verify(scenarioRepository).save(scenario);
    }

    @Test
    @DisplayName("Given applyRule true and manual inject, should not apply asset groups")
    void given_applyRuleTrueAndManualInject_should_notApplyAssetGroups() {
      // Arrange
      AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
      AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
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
      List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

      when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
          .thenReturn(assetGroupsToAdd);
      when(scenarioRepository.save(scenario)).thenReturn(scenario);
      when(injectService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS))).thenReturn(false);

      // Act
      scenarioService.updateScenario(scenario, currentTags, true);

      // Assert
      verify(injectService, never()).applyDefaultAssetGroupsToInject(any(), any());
      verify(scenarioRepository).save(scenario);
    }

    @Test
    @DisplayName("Given applyRule false, should not apply asset groups")
    void given_applyRuleFalse_should_notApplyAssetGroups() {
      // Arrange
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

      // Act
      scenarioService.updateScenario(scenario, currentTags, false);

      // Assert
      verify(injectService, never()).applyDefaultAssetGroupsToInject(any(), any());
      verify(scenarioRepository).save(scenario);
    }
  }

  private AssetGroup getAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(name);
    assetGroup.setId(name);
    return assetGroup;
  }

  @Nested
  @DisplayName("Create scenario")
  class CreateScenario {

    @Test
    @DisplayName("Given scenario with existing from, should save and keep from")
    void given_scenarioWithExistingFrom_should_saveAndKeepFrom() {
      // Arrange
      Scenario scenario = ScenarioFixture.getScenario();
      when(scenarioRepository.save(any(Scenario.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      Scenario result = scenarioService.createScenario(scenario);

      // Assert
      assertNotNull(result);
      assertEquals("simulation@mail.fr", result.getFrom());
    }

    @Test
    @DisplayName("Given scenario, should return saved scenario")
    void given_scenario_should_returnSavedScenario() {
      // Arrange
      Scenario scenario = ScenarioFixture.getScenario();
      Scenario saved = ScenarioFixture.getScenario();
      saved.setId("saved-id");
      when(scenarioRepository.save(any(Scenario.class))).thenReturn(saved);

      // Act
      Scenario result = scenarioService.createScenario(scenario);

      // Assert
      assertNotNull(result);
      assertEquals("saved-id", result.getId());
    }
  }

  @Nested
  @DisplayName("Compute emails")
  class ComputeEmails {

    @Test
    @DisplayName("Given existing from, should keep it unchanged")
    void given_existingFrom_should_keepItUnchanged() {
      // Arrange
      Scenario scenario = new Scenario();
      scenario.setFrom("existing@mail.com");

      // Act
      scenarioService.computeEmails(scenario);

      // Assert
      assertEquals("existing@mail.com", scenario.getFrom());
    }
  }

  @Nested
  @DisplayName("Retrieve scenario")
  class RetrieveScenario {

    @Test
    @DisplayName("Given existing scenario id, should return scenario")
    void given_existingScenarioId_should_returnScenario() {
      // Arrange
      Scenario scenario = new Scenario();
      scenario.setId("sc-1");
      when(scenarioRepository.findByIdAndTenant("sc-1")).thenReturn(Optional.of(scenario));

      // Act
      Scenario result = scenarioService.scenario("sc-1");

      // Assert
      assertNotNull(result);
      assertEquals("sc-1", result.getId());
    }

    @Test
    @DisplayName("Given missing scenario id, should throw ElementNotFoundException")
    void given_missingScenarioId_should_throwElementNotFoundException() {
      // Arrange
      when(scenarioRepository.findByIdAndTenant("missing")).thenReturn(Optional.empty());

      // Act & Assert
      assertThrows(
          io.openaev.rest.exception.ElementNotFoundException.class,
          () -> scenarioService.scenario("missing"));
    }

    @Test
    @DisplayName("Given existing scenario id, should delete scenario")
    void given_existingScenarioId_should_deleteScenario() {
      // Arrange
      Scenario scenario = new Scenario();
      scenario.setId("sc-1");
      when(scenarioRepository.findByIdAndTenant("sc-1")).thenReturn(Optional.of(scenario));

      // Act
      scenarioService.deleteScenario("sc-1");

      // Assert
      verify(scenarioRepository).delete(scenario);
    }
  }

  @Nested
  @DisplayName("Recurring scenarios")
  class RecurringScenarios {

    @Test
    @DisplayName("Given instant, should return recurring scenarios after that instant")
    void given_instant_should_returnRecurringScenariosAfterInstant() {
      // Arrange
      Scenario scenario = new Scenario();
      when(scenarioRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
          .thenReturn(List.of(scenario));

      // Act
      List<Scenario> result = scenarioService.recurringScenarios(java.time.Instant.now());

      // Assert
      assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Given instant, should return empty list when no outdated scenarios")
    void given_instant_should_returnEmptyListWhenNoOutdatedScenarios() {
      // Arrange
      when(scenarioRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
          .thenReturn(Collections.emptyList());

      // Act
      List<Scenario> result =
          scenarioService.potentialOutdatedRecurringScenario(java.time.Instant.now());

      // Assert
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  @DisplayName("Team management")
  class TeamManagement {

    @Test
    @DisplayName("Given scenario with players, should disable players")
    void given_scenarioWithPlayers_should_disablePlayers() {
      // Arrange
      Scenario scenario = new Scenario();
      scenario.setId("sc-1");
      scenario.setInjects(new HashSet<>());
      when(scenarioRepository.findByIdAndTenant("sc-1")).thenReturn(Optional.of(scenario));

      // Act
      Scenario result = scenarioService.disablePlayers("sc-1", "team-id", List.of("player-1"));

      // Assert
      assertNotNull(result);
      assertEquals("sc-1", result.getId());
    }
  }

  @Nested
  @DisplayName("Tag rules")
  class TagRules {

    @Test
    @DisplayName("Given new tags added, should return true")
    void given_newTagsAdded_should_returnTrue() {
      // Arrange
      Tag existingTag = TagFixture.getTag("Existing");
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setTags(Set.of(existingTag));
      when(tagRuleService.checkIfRulesApply(any(), any())).thenReturn(true);

      // Act
      boolean result = scenarioService.checkIfTagRulesApplies(scenario, List.of("new-tag-id"));

      // Assert
      assertTrue(result);
    }

    @Test
    @DisplayName("Given no new tags, should return false")
    void given_noNewTags_should_returnFalse() {
      // Arrange
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setTags(Set.of());
      when(tagRuleService.checkIfRulesApply(any(), any())).thenReturn(false);

      // Act
      boolean result = scenarioService.checkIfTagRulesApplies(scenario, List.of());

      // Assert
      assertFalse(result);
    }
  }

  @Nested
  @DisplayName("Launch validation")
  class LaunchValidation {

    @Test
    @DisplayName("Given active license, should not throw")
    void given_activeLicense_should_notThrow() {
      // Arrange
      when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
      Scenario scenario = new Scenario();
      scenario.setInjects(new HashSet<>());

      // Act & Assert
      assertDoesNotThrow(() -> scenarioService.throwIfScenarioNotLaunchable(scenario));
    }

    @Test
    @DisplayName("Given inactive license, should delegate to inject service")
    void given_inactiveLicense_should_delegateToInjectService() {
      // Arrange
      when(enterpriseEditionService.isLicenseActive(any())).thenReturn(false);
      Inject inject = new Inject();
      Scenario scenario = new Scenario();
      scenario.setInjects(new HashSet<>(List.of(inject)));

      // Act
      scenarioService.throwIfScenarioNotLaunchable(scenario);

      // Assert
      verify(injectService).throwIfInjectNotLaunchable(inject);
    }
  }

  @Nested
  @DisplayName("Replace teams")
  class ReplaceTeams {

    @Test
    @DisplayName("Given deselected team, should fully remove it and enable only new teams")
    void given_deselectedTeam_should_fullyRemoveAndEnableOnlyNewTeams() {
      // Arrange
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

      when(scenarioRepository.findByIdAndTenant(scenarioId)).thenReturn(Optional.of(scenario));
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

      // Act
      scenarioService.replaceTeams(scenarioId, List.of("team-2", "team-3", "team-3"));

      // Assert
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
    @DisplayName("Given no team removed, should not call cleanup")
    void given_noTeamRemoved_should_notCallCleanup() {
      // Arrange
      String scenarioId = "scenario-123";

      Team existingTeam = new Team();
      existingTeam.setId("team-1");
      existingTeam.setUsers(new ArrayList<>());

      Scenario scenario = new Scenario();
      scenario.setId(scenarioId);
      scenario.setTeams(new ArrayList<>(List.of(existingTeam)));

      when(scenarioRepository.findByIdAndTenant(scenarioId)).thenReturn(Optional.of(scenario));
      when(teamRepository.findAllById(any())).thenReturn(List.of(existingTeam));
      when(teamService.find(any())).thenReturn(List.of());

      // Act
      scenarioService.replaceTeams(scenarioId, List.of("team-1"));

      // Assert
      verify(scenarioTeamUserRepository, never()).deleteByScenarioIdAndTeamIds(any(), any());
      verify(injectRepository, never()).removeTeamsForScenario(any(), any());
      verify(lessonsCategoryRepository, never()).removeTeamsForScenario(any(), any());
    }
  }
}
