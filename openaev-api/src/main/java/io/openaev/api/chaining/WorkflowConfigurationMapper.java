package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.*;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.WorkflowConfiguration;
import io.openaev.database.model.WorkflowScope;
import io.openaev.database.model.WorkflowScopeRule;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WorkflowConfigurationMapper {

  private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");

  private static final Pattern IPV4_SUBNET_PATTERN =
      Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}/(3[0-2]|[0-2]?\\d)$");

  /**
   * Applies a {@link WorkflowConfigurationInput} DTO onto an existing {@link WorkflowConfiguration}
   * entity by copying each flat field directly. Also wires the bidirectional JPA back-reference on
   * the new {@link WorkflowScope} so that its owning-side foreign key is persisted correctly.
   *
   * @param input the input DTO to read from
   * @param configuration the entity to update in place
   */
  public void applyInput(WorkflowConfigurationInput input, WorkflowConfiguration configuration) {
    // Rate limit
    configuration.setRateLimitEnabled(input.isRateLimitEnabled());
    configuration.setMaxAttempts(input.getMaxAttempts());
    configuration.setMaxTemporalRateSeconds(input.getMaxTemporalRateSeconds());
    // Timeout
    configuration.setTimeoutEnabled(input.isTimeoutEnabled());
    configuration.setTimeoutSeconds(input.getTimeoutSeconds());
    // Safe mode
    configuration.setSafeModeEnabled(input.isSafeModeEnabled());
    // WorkflowScope
    WorkflowScope scope = toScope(input.getWorkflowScope());
    if (scope != null) {
      scope.setWorkflowConfiguration(configuration);
    }
    configuration.setWorkflowScope(scope);
  }

  /**
   * Maps a {@link WorkflowScopeInput} DTO to a {@link WorkflowScope} entity.
   *
   * @param input the scope input, may be {@code null}
   * @return the mapped scope entity, or {@code null} if input is {@code null}
   */
  public WorkflowScope toScope(WorkflowScopeInput input) {
    if (input == null) {
      return null;
    }
    WorkflowScope workflowScope = new WorkflowScope();
    List<WorkflowScopeRule> rules =
        input.getWorkflowScopeRules() == null
            ? Collections.emptyList()
            : input.getWorkflowScopeRules().stream()
                .map(ruleInput -> toScopeRule(ruleInput, workflowScope))
                .toList();
    workflowScope.setWorkflowScopeRules(rules);
    return workflowScope;
  }

  /**
   * Maps a {@link WorkflowConfiguration} entity to its {@link WorkflowConfigurationOutput} DTO.
   *
   * @param configuration the entity to map
   * @return the mapped output DTO
   */
  public WorkflowConfigurationOutput toOutput(WorkflowConfiguration configuration) {
    return WorkflowConfigurationOutput.builder()
        .rateLimitEnabled(configuration.isRateLimitEnabled())
        .maxAttempts(configuration.getMaxAttempts())
        .maxTemporalRateSeconds(configuration.getMaxTemporalRateSeconds())
        .timeoutEnabled(configuration.isTimeoutEnabled())
        .timeoutSeconds(configuration.getTimeoutSeconds())
        .safeModeEnabled(configuration.isSafeModeEnabled())
        .workflowScope(toWorkflowScopeOutput(configuration.getWorkflowScope()))
        .build();
  }

  // -- Private helpers --

  /**
   * Maps a {@link WorkflowScope} entity to its {@link WorkflowScopeOutput} DTO.
   *
   * @param workflowScope the scope entity, may be {@code null}
   * @return the mapped output DTO, or {@code null} if the scope is {@code null}
   */
  private WorkflowScopeOutput toWorkflowScopeOutput(WorkflowScope workflowScope) {
    if (workflowScope == null) {
      return null;
    }
    List<WorkflowScopeRuleOutput> ruleOutputs =
        workflowScope.getWorkflowScopeRules() == null
            ? Collections.emptyList()
            : workflowScope.getWorkflowScopeRules().stream().map(this::toScopeRuleOutput).toList();
    return WorkflowScopeOutput.builder().workflowScopeRules(ruleOutputs).build();
  }

  private WorkflowScopeRuleOutput toScopeRuleOutput(WorkflowScopeRule rule) {
    return WorkflowScopeRuleOutput.builder()
        .selectedMode(rule.getSelectedMode())
        .ruleSource(rule.getRuleSource())
        .ruleValue(rule.getRuleValue())
        .ruleValueType(rule.getValueType())
        .build();
  }

  private WorkflowScopeRule toScopeRule(WorkflowScopeRuleInput input, WorkflowScope workflowScope) {
    WorkflowScopeRule rule = new WorkflowScopeRule();
    rule.setSelectedMode(input.getSelectedMode());
    rule.setRuleSource(input.getRuleSource());
    rule.setRuleValue(input.getRuleValue());
    rule.setWorkflowScope(workflowScope);
    rule.setValueType(detectValueType(input));
    return rule;
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
