package io.openaev.api.chaining;

import static io.openaev.api.chaining.WorkflowConfigurationMapper.toOutput;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.api.chaining.dto.WorkflowConfigurationOutput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleOutput;
import io.openaev.database.model.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("WorkflowConfigurationMapper")
class WorkflowConfigurationMapperTest {

  @Nested
  @DisplayName("toOutput — inline configuration fields")
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
  @DisplayName("toOutput — scope rules")
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
}
