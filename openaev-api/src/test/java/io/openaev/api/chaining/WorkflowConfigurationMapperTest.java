package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.openaev.api.chaining.dto.WorkflowConfigurationOutput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleOutput;
import io.openaev.database.model.*;
import io.openaev.service.chaining.ScopeSnapshotService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WorkflowConfigurationMapper")
class WorkflowConfigurationMapperTest {

  // Status computation is covered separately; here a mock returns null (draft / no snapshot).
  private final WorkflowConfigurationMapper mapper =
      new WorkflowConfigurationMapper(mock(ScopeSnapshotService.class));

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
      assertEquals("S*******3", output.getWorkflowScopeVariables().get(0).getValue());
      assertEquals("ABC******456", output.getWorkflowScopeVariables().get(1).getValue());
      assertEquals("XYZ***654", output.getWorkflowScopeVariables().get(2).getValue());
      assertEquals("NotSensitive", output.getWorkflowScopeVariables().get(3).getValue());
    }
  }
}
