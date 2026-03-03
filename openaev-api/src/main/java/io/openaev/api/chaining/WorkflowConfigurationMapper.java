package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.*;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WorkflowConfigurationMapper {

  private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");

  private static final Pattern IPV4_SUBNET_PATTERN =
      Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}/(3[0-2]|[0-2]?\\d)$");

  // -- Input → Entity --

  /**
   * Applies a {@link WorkflowConfigurationInput} DTO onto an existing {@link Workflow} entity by
   * copying each flat field directly and replacing the scope-rule list (orphan removal handles
   * cleanup of old rows).
   *
   * @param input the input DTO to read from
   * @param workflow the workflow entity to update in place
   */
  public void applyInput(WorkflowConfigurationInput input, Workflow workflow) {
    // Rate limit
    workflow.setRateLimitEnabled(input.isRateLimitEnabled());
    workflow.setMaxAttempts(input.getMaxAttempts());
    workflow.setMaxTemporalRateSeconds(input.getMaxTemporalRateSeconds());
    // Timeout
    workflow.setTimeoutEnabled(input.isTimeoutEnabled());
    workflow.setTimeoutSeconds(input.getTimeoutSeconds());
    // Safe mode
    workflow.setSafeModeEnabled(input.isSafeModeEnabled());
    // Scope rules — replace the whole list; orphanRemoval cleans up stale rows
    workflow.setWorkflowScopeRules(toScopeRules(input.getWorkflowScopeRules(), workflow));
  }

  // -- Entity → Output --

  /**
   * Maps a {@link Workflow} entity to its {@link WorkflowConfigurationOutput} DTO.
   *
   * @param workflow the entity to map
   * @return the mapped output DTO
   */
  public WorkflowConfigurationOutput toOutput(Workflow workflow) {
    return WorkflowConfigurationOutput.builder()
        .rateLimitEnabled(workflow.isRateLimitEnabled())
        .maxAttempts(workflow.getMaxAttempts())
        .maxTemporalRateSeconds(workflow.getMaxTemporalRateSeconds())
        .timeoutEnabled(workflow.isTimeoutEnabled())
        .timeoutSeconds(workflow.getTimeoutSeconds())
        .safeModeEnabled(workflow.isSafeModeEnabled())
        .workflowScopeRules(toScopeRuleOutputList(workflow.getWorkflowScopeRules()))
        .build();
  }

  // -- Private helpers --

  /**
   * Maps a list of {@link WorkflowScopeRuleInput} DTOs to {@link WorkflowScopeRule} entities linked
   * to the given workflow.
   *
   * @param ruleInputs the rule inputs, may be {@code null}
   * @param workflow the owning workflow
   * @return a mutable list of scope rule entities (empty if {@code ruleInputs} is {@code null})
   */
  private List<WorkflowScopeRule> toScopeRules(
      List<WorkflowScopeRuleInput> ruleInputs, Workflow workflow) {
    if (ruleInputs == null) {
      return new ArrayList<>();
    }
    return ruleInputs.stream().map(ruleInput -> toScopeRule(ruleInput, workflow)).toList();
  }

  private WorkflowScopeRule toScopeRule(WorkflowScopeRuleInput input, Workflow workflow) {
    WorkflowScopeRule rule = new WorkflowScopeRule();
    rule.setSelectedMode(input.getSelectedMode());
    rule.setRuleSource(input.getRuleSource());
    rule.setRuleValue(input.getRuleValue());
    rule.setWorkflow(workflow);
    rule.setValueType(detectValueType(input));
    return rule;
  }

  /**
   * Maps a list of {@link WorkflowScopeRule} entities to a list of {@link WorkflowScopeRuleOutput}
   * DTOs.
   *
   * @param rules the scope rules, may be {@code null} or empty
   * @return the mapped output list, or {@code null} if there are no rules
   */
  private List<WorkflowScopeRuleOutput> toScopeRuleOutputList(List<WorkflowScopeRule> rules) {
    if (rules == null || rules.isEmpty()) {
      return null;
    }
    return rules.stream().map(this::toScopeRuleOutput).toList();
  }

  private WorkflowScopeRuleOutput toScopeRuleOutput(WorkflowScopeRule rule) {
    return WorkflowScopeRuleOutput.builder()
        .selectedMode(rule.getSelectedMode())
        .ruleSource(rule.getRuleSource())
        .ruleValue(rule.getRuleValue())
        .ruleValueType(rule.getValueType())
        .build();
  }

  /**
   * Determines the {@link ScopeRuleValueType} for a rule. When the source explicitly identifies an
   * asset or asset group, the type is resolved from the source; otherwise it is inferred from the
   * rule value string.
   */
  private ScopeRuleValueType detectValueType(WorkflowScopeRuleInput input) {
    if (input.getRuleSource() != null) {
      return switch (input.getRuleSource()) {
        case ASSET -> ScopeRuleValueType.ASSET_ID;
        case ASSET_GROUP -> ScopeRuleValueType.ASSET_GROUP_ID;
        default -> resolveValueTypeFromString(input.getRuleValue());
      };
    }
    return resolveValueTypeFromString(input.getRuleValue());
  }

  /**
   * Infers the {@link ScopeRuleValueType} from a raw string value by testing it against known IP
   * and subnet patterns. Falls back to {@link ScopeRuleValueType#DOMAIN} for anything else
   * (hostnames, FQDNs, etc.).
   */
  private ScopeRuleValueType resolveValueTypeFromString(String value) {
    String trimmed = value != null ? value.trim() : "";
    if (IPV4_SUBNET_PATTERN.matcher(trimmed).matches()) {
      return ScopeRuleValueType.IP_SUBNET;
    }
    if (IPV4_PATTERN.matcher(trimmed).matches()) {
      return ScopeRuleValueType.IP;
    }
    return ScopeRuleValueType.DOMAIN;
  }
}
