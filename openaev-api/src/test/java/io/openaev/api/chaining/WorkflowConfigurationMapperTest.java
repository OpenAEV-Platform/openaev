package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.api.chaining.dto.WorkflowConfigurationOutput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleOutput;
import io.openaev.database.model.*;
import io.openaev.service.chaining.ScopeSnapshotService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WorkflowConfigurationMapper")
class WorkflowConfigurationMapperTest {

  // Status computation is covered separately; here a mock returns null (draft / no snapshot).
  private final ScopeSnapshotService snapshotService = mock(ScopeSnapshotService.class);
  private final WorkflowConfigurationMapper mapper =
      new WorkflowConfigurationMapper(snapshotService);

  private WorkflowConfigurationOutput toOutput(Workflow workflow) {
    return mapper.toOutput(workflow);
  }

  @Nested
  @DisplayName("toOutput - inline configuration fields")
  class InlineConfigurationFieldsTests {

    @Test
    @DisplayName("should map all inline configuration fields from workflow")
    void shouldMapAllInlineConfigurationFields() {
      // Arrange
      Workflow workflow =
          Workflow.builder()
              .rateLimitEnabled(true)
              .maxAttempts(5)
              .maxTemporalRateSeconds(30L)
              .timeoutEnabled(true)
              .timeoutSeconds(120L)
              .safeModeEnabled(false)
              .build();

      // Act
      WorkflowConfigurationOutput output = toOutput(workflow);

      // Assert
      assertTrue(output.isRateLimitEnabled());
      assertEquals(5, output.getMaxAttempts());
      assertEquals(30L, output.getMaxTemporalRateSeconds());
      assertTrue(output.isTimeoutEnabled());
      assertEquals(120L, output.getTimeoutSeconds());
      assertFalse(output.isSafeModeEnabled());
    }

    @Test
    @DisplayName("should return empty scope-rules list when workflow has no rules")
    void shouldReturnEmptyScopeRulesWhenNone() {
      // Arrange
      Workflow workflow = Workflow.builder().build();

      // Act
      WorkflowConfigurationOutput output = toOutput(workflow);

      // Assert
      assertNotNull(output.getWorkflowScopeRules());
      assertTrue(output.getWorkflowScopeRules().isEmpty());
    }
  }

  @Nested
  @DisplayName("toOutput - scope rules")
  class ScopeRuleOutputTests {

    @Test
    @DisplayName("should map scope rule fields to output DTO")
    void shouldMapScopeRuleFields() {
      // Arrange
      WorkflowScopeRule rule =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("10.0.0.1")
              .build();

      Workflow workflow =
          Workflow.builder().workflowScopeRules(new ArrayList<>(List.of(rule))).build();

      // Act
      WorkflowConfigurationOutput output = toOutput(workflow);

      // Assert
      assertEquals(1, output.getWorkflowScopeRules().size());
      WorkflowScopeRuleOutput ruleOutput = output.getWorkflowScopeRules().getFirst();
      assertEquals(ScopeRuleSelectedMode.ALLOWLIST, ruleOutput.getSelectedMode());
      assertEquals(ScopeRuleSource.MANUAL, ruleOutput.getRuleSource());
      assertEquals("10.0.0.1", ruleOutput.getRuleValue());
    }

    @Test
    @DisplayName("should map multiple scope rules preserving order")
    void shouldMapMultipleScopeRules() {
      // Arrange
      WorkflowScopeRule allowlist =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("10.0.0.1")
              .build();
      WorkflowScopeRule denylist =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.DENYLIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("192.168.0.0/16")
              .build();

      Workflow workflow =
          Workflow.builder()
              .workflowScopeRules(new ArrayList<>(List.of(allowlist, denylist)))
              .build();

      // Act
      WorkflowConfigurationOutput output = toOutput(workflow);

      // Assert
      assertEquals(2, output.getWorkflowScopeRules().size());
      assertEquals(
          ScopeRuleSelectedMode.ALLOWLIST, output.getWorkflowScopeRules().get(0).getSelectedMode());
      assertEquals(
          ScopeRuleSelectedMode.DENYLIST, output.getWorkflowScopeRules().get(1).getSelectedMode());
    }
  }

  @Nested
  @DisplayName("toOutput - assets the reader cannot see")
  class HiddenAssetTests {

    private static final String TENANT_ID = "tenant-1";

    private WorkflowScopeRule assetRule(String assetId, String name) {
      return WorkflowScopeRule.builder()
          .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
          .ruleSource(ScopeRuleSource.ASSET)
          .ruleValue(assetId)
          .ruleValueLabel(name)
          .snapshotStart(assetPhoto(assetId, name))
          .build();
    }

    private ScopeRuleSnapshot assetPhoto(String assetId, String name) {
      return ScopeRuleSnapshot.builder().label(name).assets(List.of(asset(assetId, name))).build();
    }

    private ScopeRuleSnapshot.AssetSnapshot asset(String assetId, String name) {
      return ScopeRuleSnapshot.AssetSnapshot.builder()
          .id(assetId)
          .name(name)
          .agentsCount(1)
          .executors(List.of())
          .build();
    }

    private Workflow workflowOf(WorkflowScopeRule... rules) {
      Tenant tenant = new Tenant();
      tenant.setId(TENANT_ID);
      Workflow workflow =
          Workflow.builder().workflowScopeRules(new ArrayList<>(List.of(rules))).build();
      workflow.setTenant(tenant);
      return workflow;
    }

    @Test
    @DisplayName("should not return the rule of an asset the reader is not cleared for")
    void given_ruleOnHiddenAsset_should_dropTheRule() {
      // Arrange
      when(snapshotService.hiddenAssetIds(eq(TENANT_ID), any())).thenReturn(Set.of("red-asset"));
      Workflow workflow =
          workflowOf(assetRule("green-asset", "Green server"), assetRule("red-asset", "Red dc"));

      // Act
      List<WorkflowScopeRuleOutput> rules = toOutput(workflow).getWorkflowScopeRules();

      // Assert
      assertEquals(1, rules.size());
      assertEquals("green-asset", rules.getFirst().getRuleValue());
      assertTrue(
          rules.stream().noneMatch(rule -> "Red dc".equals(rule.getRuleValueLabel())),
          "the name of the hidden asset must not leave");
    }

    @Test
    @DisplayName("should ask which assets are hidden for every asset a rule or a group photo names")
    void given_rulesAndGroupPhotos_should_lookUpEveryAssetTheyName() {
      // Arrange
      WorkflowScopeRule group =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET_GROUP)
              .ruleValue("group-1")
              .snapshotStart(
                  ScopeRuleSnapshot.builder()
                      .label("Servers")
                      .assets(List.of(asset("member-1", "m1"), asset("member-2", "m2")))
                      .build())
              .build();
      Workflow workflow = workflowOf(assetRule("single-1", "Single"), group);

      // Act
      toOutput(workflow);

      // Assert
      verify(snapshotService).hiddenAssetIds(TENANT_ID, Set.of("single-1", "member-1", "member-2"));
    }

    @Test
    @DisplayName("should return a group photo without the members the reader cannot see")
    void given_groupWithHiddenMember_should_cutThePhoto() {
      // Arrange
      when(snapshotService.hiddenAssetIds(eq(TENANT_ID), any())).thenReturn(Set.of("red-member"));
      WorkflowScopeRule group =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET_GROUP)
              .ruleValue("group-1")
              .snapshotStart(
                  ScopeRuleSnapshot.builder()
                      .label("Servers")
                      .assets(List.of(asset("green-member", "g"), asset("red-member", "r")))
                      .build())
              .snapshotEnd(
                  ScopeRuleSnapshot.builder()
                      .label("Servers")
                      .assets(List.of(asset("green-member", "g"), asset("red-member", "r")))
                      .build())
              .build();

      // Act
      WorkflowScopeRuleOutput output =
          toOutput(workflowOf(group)).getWorkflowScopeRules().getFirst();

      // Assert
      assertEquals("Servers", output.getSnapshotStartLabel());
      assertEquals(
          List.of("green-member"),
          output.getSnapshotStartAssets().stream().map(a -> a.getId()).toList());
      assertEquals(
          List.of("green-member"),
          output.getSnapshotEndAssets().stream().map(a -> a.getId()).toList());
    }

    @Test
    @DisplayName("should compute the status of a rule with the hidden assets taken out")
    void given_hiddenAssets_should_computeTheStatusWithoutThem() {
      // Arrange
      when(snapshotService.hiddenAssetIds(eq(TENANT_ID), any())).thenReturn(Set.of("red-member"));
      WorkflowScopeRule group =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET_GROUP)
              .ruleValue("group-1")
              .snapshotStart(
                  ScopeRuleSnapshot.builder()
                      .label("Servers")
                      .assets(List.of(asset("red-member", "r")))
                      .build())
              .build();
      when(snapshotService.computeStatus(group, Set.of("red-member")))
          .thenReturn(ScopeRuleSnapshotStatus.RESOLVED);

      // Act
      WorkflowScopeRuleOutput output =
          toOutput(workflowOf(group)).getWorkflowScopeRules().getFirst();

      // Assert
      assertEquals(ScopeRuleSnapshotStatus.RESOLVED, output.getStatus());
    }

    @Test
    @DisplayName("should keep the rule of a deleted asset: only an asset that exists is hidden")
    void given_deletedAsset_should_keepTheRule() {
      // Arrange: the lookup returns the assets that EXIST but are not visible, so a deleted
      // asset is not in it and its rule keeps showing as deleted.
      when(snapshotService.hiddenAssetIds(eq(TENANT_ID), any())).thenReturn(Set.of());
      Workflow workflow = workflowOf(assetRule("gone-asset", "Old server"));

      // Act
      List<WorkflowScopeRuleOutput> rules = toOutput(workflow).getWorkflowScopeRules();

      // Assert
      assertEquals(1, rules.size());
    }
  }

  @Nested
  @DisplayName("toOutput - audience snapshot labels")
  class AudienceSnapshotLabelTests {

    @Test
    @DisplayName("should emit the frozen team name as the launch snapshot label")
    void shouldEmitResolvedAudienceLabel() {
      // Arrange
      WorkflowScopeRule rule =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.TEAM)
              .ruleValue("team-id-1")
              .snapshotStart(ScopeRuleSnapshot.builder().label("It team").build())
              .build();
      Workflow workflow =
          Workflow.builder().workflowScopeRules(new ArrayList<>(List.of(rule))).build();

      // Act
      WorkflowScopeRuleOutput output = toOutput(workflow).getWorkflowScopeRules().getFirst();

      // Assert
      assertEquals("It team", output.getSnapshotStartLabel());
    }

    @Test
    @DisplayName(
        "should suppress a degraded audience label (raw id frozen pre-resolution) so the frontend resolves live")
    void shouldSuppressDegradedAudienceLabel() {
      // Arrange: photo frozen before TEAM / PLAYER resolution existed - label == raw id. Emitting
      // it would render a UUID chip on the simulation scope tab.
      WorkflowScopeRule rule =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.TEAM)
              .ruleValue("team-id-1")
              .snapshotStart(ScopeRuleSnapshot.builder().label("team-id-1").build())
              .build();
      Workflow workflow =
          Workflow.builder().workflowScopeRules(new ArrayList<>(List.of(rule))).build();

      // Act
      WorkflowScopeRuleOutput output = toOutput(workflow).getWorkflowScopeRules().getFirst();

      // Assert
      assertNull(output.getSnapshotStartLabel());
      assertEquals("team-id-1", output.getRuleValue());
    }

    @Test
    @DisplayName("should keep emitting a MANUAL label even though it equals the rule value")
    void shouldKeepManualLabelEqualToValue() {
      // MANUAL / CSV labels equal the value by design - only audience rules are suppressed.
      WorkflowScopeRule rule =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("10.0.0.1")
              .snapshotStart(ScopeRuleSnapshot.builder().label("10.0.0.1").build())
              .build();
      Workflow workflow =
          Workflow.builder().workflowScopeRules(new ArrayList<>(List.of(rule))).build();

      // Act
      WorkflowScopeRuleOutput output = toOutput(workflow).getWorkflowScopeRules().getFirst();

      // Assert
      assertEquals("10.0.0.1", output.getSnapshotStartLabel());
    }
  }

  @Nested
  @DisplayName("toOutput - scope variables")
  class ScopeVariableOutputTests {

    @Test
    @DisplayName("should mask sensitive scope variable values")
    void shouldMaskSensitiveScopeVariableValues() {
      // Arrange
      ScopeVariable passwordVariable =
          ScopeVariable.builder()
              .key("passwordVar")
              .type(PrimitiveType.Password)
              .value("Secret123")
              .build();
      ScopeVariable hashVariable =
          ScopeVariable.builder()
              .key("hashVar")
              .type(PrimitiveType.Hash)
              .value("ABCDEF123456")
              .build();
      ScopeVariable keyVariable =
          ScopeVariable.builder().key("keyVar").type(PrimitiveType.Key).value("XYZ987654").build();
      ScopeVariable textVariable =
          ScopeVariable.builder()
              .key("textVar")
              .type(PrimitiveType.Text)
              .value("NotSensitive")
              .build();
      Workflow workflow =
          Workflow.builder()
              .workflowScopeVariables(
                  new ArrayList<>(
                      List.of(passwordVariable, hashVariable, keyVariable, textVariable)))
              .build();

      // Act
      WorkflowConfigurationOutput output = toOutput(workflow);

      // Assert
      assertEquals("Se******", output.getWorkflowScopeVariables().get(0).getValue());
      assertEquals("AB******", output.getWorkflowScopeVariables().get(1).getValue());
      assertEquals("XY******", output.getWorkflowScopeVariables().get(2).getValue());
      assertEquals("NotSensitive", output.getWorkflowScopeVariables().get(3).getValue());
    }
  }
}
