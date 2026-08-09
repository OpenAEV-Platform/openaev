package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.AssetSnapshotOutput;
import io.openaev.api.chaining.dto.ScopeVariableOutput;
import io.openaev.api.chaining.dto.SecurityPlatformSnapshotOutput;
import io.openaev.api.chaining.dto.WorkflowConfigurationOutput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleOutput;
import io.openaev.database.model.ScopeRuleSnapshot;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeVariable;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.service.chaining.ScopeSnapshotService;
import io.openaev.utils.PrimitiveValueMaskingUtils;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link Workflow} to its configuration output. A Spring bean (not a static utility) because
 * the scope-rule change status is recomputed at read time against the live state via {@link
 * ScopeSnapshotService}. See ADR-006.
 */
@Component
@RequiredArgsConstructor
public class WorkflowConfigurationMapper {

  private final ScopeSnapshotService scopeSnapshotService;

  /**
   * Maps a {@link Workflow} entity to its {@link WorkflowConfigurationOutput} DTO.
   *
   * @param workflow the entity to map (TEMPLATE for draft/scenario, RUN for a launched simulation)
   * @return the mapped output DTO
   */
  public WorkflowConfigurationOutput toOutput(Workflow workflow) {
    List<WorkflowScopeRule> rules = workflow.getWorkflowScopeRules();
    return WorkflowConfigurationOutput.builder()
        .rateLimitEnabled(workflow.isRateLimitEnabled())
        .maxAttempts(workflow.getMaxAttempts())
        .maxTemporalRateSeconds(workflow.getMaxTemporalRateSeconds())
        .timeoutEnabled(workflow.isTimeoutEnabled())
        .timeoutSeconds(workflow.getTimeoutSeconds())
        .safeModeEnabled(workflow.isSafeModeEnabled())
        .workflowScopeRules(toScopeRuleOutputs(rules))
        .securityPlatforms(toSecurityPlatformOutputs(rules))
        .workflowScopeVariables(toScopeVariableOutputList(workflow.getWorkflowScopeVariables()))
        .build();
  }

  // -- SCOPE RULES (asset / group / manual) --

  private List<WorkflowScopeRuleOutput> toScopeRuleOutputs(List<WorkflowScopeRule> rules) {
    if (rules == null) {
      return List.of();
    }
    return rules.stream().map(this::toScopeRuleOutput).filter(Objects::nonNull).toList();
  }

  private WorkflowScopeRuleOutput toScopeRuleOutput(WorkflowScopeRule rule) {
    if (rule.getRuleSource() == ScopeRuleSource.SECURITY_PLATFORM) {
      return null;
    }
    ScopeRuleSnapshot launch = rule.getSnapshotStart();
    ScopeRuleSnapshot end = rule.getSnapshotEnd();
    return WorkflowScopeRuleOutput.builder()
        .id(rule.getId())
        .selectedMode(rule.getSelectedMode())
        .ruleSource(rule.getRuleSource())
        .ruleValue(rule.getRuleValue())
        // The template-time label snapshot keeps its own meaning (deleted-asset fallback for
        // draft / scenario rules); the launch-time display label is snapshotStartLabel below.
        .ruleValueLabel(rule.getRuleValueLabel())
        // null for draft / scenario / pre-ADR-006 rules (no launch snapshot) → front resolves live.
        .status(scopeSnapshotService.computeStatus(rule))
        .snapshotStartLabel(launch != null ? launch.getLabel() : null)
        .snapshotStartAssets(toAssetSnapshotOutputs(launch))
        .snapshotEndLabel(end != null ? end.getLabel() : null)
        .snapshotEndAssets(toAssetSnapshotOutputs(end))
        .build();
  }

  private List<AssetSnapshotOutput> toAssetSnapshotOutputs(ScopeRuleSnapshot snapshot) {
    if (snapshot == null || snapshot.getAssets() == null) {
      return List.of();
    }
    return snapshot.getAssets().stream()
        .map(
            a ->
                AssetSnapshotOutput.builder()
                    .id(a.getId())
                    .name(a.getName())
                    .agentsCount(a.getAgentsCount())
                    .executors(a.getExecutors())
                    .build())
        .toList();
  }

  // -- SECURITY PLATFORMS (separate list) --

  private List<SecurityPlatformSnapshotOutput> toSecurityPlatformOutputs(
      List<WorkflowScopeRule> rules) {
    if (rules == null) {
      return List.of();
    }
    return rules.stream()
        .filter(r -> r.getRuleSource() == ScopeRuleSource.SECURITY_PLATFORM)
        .map(this::toSecurityPlatformOutput)
        .toList();
  }

  private SecurityPlatformSnapshotOutput toSecurityPlatformOutput(WorkflowScopeRule rule) {
    // Current effective photo: the end snapshot once the run is over, the launch snapshot while
    // running.
    ScopeRuleSnapshot current =
        rule.getSnapshotEnd() != null ? rule.getSnapshotEnd() : rule.getSnapshotStart();
    ScopeRuleSnapshot.SecurityPlatformSnapshot sp =
        current != null ? current.getSecurityPlatform() : null;
    return SecurityPlatformSnapshotOutput.builder()
        .id(sp != null ? sp.getId() : rule.getRuleValue())
        .name(current != null ? current.getLabel() : null)
        .type(sp != null ? sp.getType() : null)
        .updatedAt(sp != null ? sp.getUpdatedAt() : null)
        .status(scopeSnapshotService.computeStatus(rule))
        .build();
  }

  // -- SCOPE VARIABLES --

  private List<ScopeVariableOutput> toScopeVariableOutputList(List<ScopeVariable> variables) {
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
        .value(PrimitiveValueMaskingUtils.maskForDisplay(variable.getType(), variable.getValue()))
        .description(variable.getDescription())
        .build();
  }
}
