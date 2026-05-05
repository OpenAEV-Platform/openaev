package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.ScopeVariableOutput;
import io.openaev.api.chaining.dto.WorkflowConfigurationOutput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleOutput;
import io.openaev.database.model.ScopeVariable;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import java.util.List;

public class WorkflowConfigurationMapper {

  private WorkflowConfigurationMapper() {}

  /**
   * Maps a {@link Workflow} entity to its {@link WorkflowConfigurationOutput} DTO.
   *
   * @param workflow the entity to map
   * @return the mapped output DTO
   */
  public static WorkflowConfigurationOutput toOutput(Workflow workflow) {
    return WorkflowConfigurationOutput.builder()
        .rateLimitEnabled(workflow.isRateLimitEnabled())
        .maxAttempts(workflow.getMaxAttempts())
        .maxTemporalRateSeconds(workflow.getMaxTemporalRateSeconds())
        .timeoutEnabled(workflow.isTimeoutEnabled())
        .timeoutSeconds(workflow.getTimeoutSeconds())
        .safeModeEnabled(workflow.isSafeModeEnabled())
        .workflowScopeRules(toScopeRuleOutputList(workflow.getWorkflowScopeRules()))
        .workflowScopeVariables(toScopeVariableOutputList(workflow.getWorkflowScopeVariables()))
        .build();
  }

  private static List<WorkflowScopeRuleOutput> toScopeRuleOutputList(
      List<WorkflowScopeRule> rules) {
    if (rules == null || rules.isEmpty()) {
      return List.of();
    }
    return rules.stream().map(WorkflowConfigurationMapper::toScopeRuleOutput).toList();
  }

  private static WorkflowScopeRuleOutput toScopeRuleOutput(WorkflowScopeRule rule) {
    return WorkflowScopeRuleOutput.builder()
        .id(rule.getId())
        .selectedMode(rule.getSelectedMode())
        .ruleSource(rule.getRuleSource())
        .ruleValue(rule.getRuleValue())
        .build();
  }

  private static List<ScopeVariableOutput> toScopeVariableOutputList(
      List<ScopeVariable> variables) {
    if (variables == null || variables.isEmpty()) {
      return List.of();
    }
    return variables.stream().map(WorkflowConfigurationMapper::toScopeVariableOutput).toList();
  }

  private static ScopeVariableOutput toScopeVariableOutput(ScopeVariable variable) {
    return ScopeVariableOutput.builder()
        .id(variable.getId())
        .key(variable.getKey())
        .type(variable.getType())
        .value(variable.getValue())
        .description(variable.getDescription())
        .build();
  }
}
