package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TargetType;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
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
  @Mock private InjectService injectService;
  @Mock private TagRuleService tagRuleService;
  @Mock private ScenarioRepository scenarioRepository;
  @InjectMocks private ScenarioService scenarioService;

  @Test
  public void testUpdateScenario_WITH_applyRule_true() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = Inject.fromTenant("tenant");
    inject1.setId("1");
    Inject inject2 = Inject.fromTenant("tenant");
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
    Inject inject1 = Inject.fromTenant("tenant");
    inject1.setId("1");
    Inject inject2 = Inject.fromTenant("tenant");
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
    Inject inject1 = Inject.fromTenant("tenant");
    inject1.setId("1");
    Inject inject2 = Inject.fromTenant("tenant");
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
      Scenario scenario = ScenarioFixture.getScenario();
      when(scenarioRepository.save(any(Scenario.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      Scenario result = scenarioService.createScenario(scenario);

      assertNotNull(result);
      assertEquals("simulation@mail.fr", result.getFrom());
    }

    @Test
    void shouldReturnSavedScenario() {
      Scenario scenario = ScenarioFixture.getScenario();
      Scenario saved = ScenarioFixture.getScenario();
      saved.setId("saved-id");
      when(scenarioRepository.save(any(Scenario.class))).thenReturn(saved);

      Scenario result = scenarioService.createScenario(scenario);

      assertNotNull(result);
      assertEquals("saved-id", result.getId());
    }
  }

  @Nested
  class ComputeEmails {

    @Test
    void shouldKeepExistingFrom_whenAlreadySet() {
      Scenario scenario = Scenario.fromTenant("tenant");
      scenario.setFrom("existing@mail.com");

      scenarioService.computeEmails(scenario);

      assertEquals("existing@mail.com", scenario.getFrom());
    }
  }

  @Nested
  class RecurringScenarios {

    @Test
    void shouldReturnRecurringScenarios_afterInstant() {
      Scenario scenario = Scenario.fromTenant("tenant");
      when(scenarioRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
          .thenReturn(List.of(scenario));

      List<Scenario> result = scenarioService.recurringScenarios(java.time.Instant.now());

      assertEquals(1, result.size());
    }

    @Test
    void shouldReturnPotentiallyOutdatedScenarios() {
      when(scenarioRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
          .thenReturn(Collections.emptyList());

      List<Scenario> result =
          scenarioService.potentialOutdatedRecurringScenario(java.time.Instant.now());

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class TagRules {

    @Test
    void shouldReturnTrue_whenNewTagsAdded() {
      Tag existingTag = TagFixture.getTag("Existing");
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setTags(Set.of(existingTag));
      when(tagRuleService.checkIfRulesApply(any(), any())).thenReturn(true);

      boolean result = scenarioService.checkIfTagRulesApplies(scenario, List.of("new-tag-id"));

      assertTrue(result);
    }

    @Test
    void shouldReturnFalse_whenNoNewTags() {
      Scenario scenario = ScenarioFixture.getScenario();
      scenario.setTags(Set.of());
      when(tagRuleService.checkIfRulesApply(any(), any())).thenReturn(false);

      boolean result = scenarioService.checkIfTagRulesApplies(scenario, List.of());

      assertFalse(result);
    }
  }

  @Nested
  class LaunchValidation {

    @Test
    void shouldNotThrow_whenLicenseActive() {
      when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
      Scenario scenario = Scenario.fromTenant("tenant");
      scenario.setInjects(new HashSet<>());

      assertDoesNotThrow(() -> scenarioService.throwIfScenarioNotLaunchable(scenario));
    }

    @Test
    void shouldDelegateToInjectService_whenLicenseNotActive() {
      when(enterpriseEditionService.isLicenseActive(any())).thenReturn(false);
      Inject inject = Inject.fromTenant("tenant");
      Scenario scenario = Scenario.fromTenant("tenant");
      scenario.setInjects(new HashSet<>(List.of(inject)));

      scenarioService.throwIfScenarioNotLaunchable(scenario);

      verify(injectService).throwIfInjectNotLaunchable(inject);
    }
  }
}
