package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
}
