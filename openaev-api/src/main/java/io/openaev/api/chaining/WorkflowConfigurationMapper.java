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
import io.openaev.utils.SensitiveValueMaskingUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    Set<String> hidden = hiddenAssetIds(workflow, rules);
    return WorkflowConfigurationOutput.builder()
        .rateLimitEnabled(workflow.isRateLimitEnabled())
        .maxAttempts(workflow.getMaxAttempts())
        .maxTemporalRateSeconds(workflow.getMaxTemporalRateSeconds())
        .timeoutEnabled(workflow.isTimeoutEnabled())
        .timeoutSeconds(workflow.getTimeoutSeconds())
        .safeModeEnabled(workflow.isSafeModeEnabled())
        .workflowScopeRules(toScopeRuleOutputs(rules, hidden))
        .securityPlatforms(toSecurityPlatformOutputs(rules))
        .workflowScopeVariables(toScopeVariableOutputList(workflow.getWorkflowScopeVariables()))
        .build();
  }

  // -- ASSETS THE READER CANNOT SEE --

  /**
   * The assets a rule names that exist but the reader is not cleared for. The rules and their
   * photos are stored copies, so the marking filter does not reach them: the reader's clearance is
   * applied here instead, as it is to every other read of an asset. A rule on such an asset is not
   * returned at all (as a hidden asset is absent from a classic simulation) and the photos of a
   * group are returned without such members.
   */
  private Set<String> hiddenAssetIds(Workflow workflow, List<WorkflowScopeRule> rules) {
    if (rules == null || rules.isEmpty()) {
      return Set.of();
    }
    Set<String> assetIds = new HashSet<>();
    for (WorkflowScopeRule rule : rules) {
      if (isAssetRule(rule)) {
        assetIds.add(rule.getRuleValue());
      } else if (rule.getRuleSource() == ScopeRuleSource.ASSET_GROUP) {
        collectAssetIds(rule.getSnapshotStart(), assetIds);
        collectAssetIds(rule.getSnapshotEnd(), assetIds);
      }
    }
    if (assetIds.isEmpty()) {
      return Set.of();
    }
    String tenantId = workflow.getTenant() == null ? null : workflow.getTenant().getId();
    return scopeSnapshotService.hiddenAssetIds(tenantId, assetIds);
  }

  private static boolean isAssetRule(WorkflowScopeRule rule) {
    return rule.getRuleSource() == ScopeRuleSource.ASSET;
  }

  private static void collectAssetIds(ScopeRuleSnapshot photo, Set<String> into) {
    if (photo != null && photo.getAssets() != null) {
      photo.getAssets().forEach(asset -> into.add(asset.getId()));
    }
  }

  // -- SCOPE RULES (asset / group / manual) --

  private List<WorkflowScopeRuleOutput> toScopeRuleOutputs(
      List<WorkflowScopeRule> rules, Set<String> hidden) {
    if (rules == null) {
      return List.of();
    }
    return rules.stream()
        .filter(rule -> !(isAssetRule(rule) && hidden.contains(rule.getRuleValue())))
        .map(rule -> toScopeRuleOutput(rule, hidden))
        .filter(Objects::nonNull)
        .toList();
  }

  private WorkflowScopeRuleOutput toScopeRuleOutput(WorkflowScopeRule rule, Set<String> hidden) {
    if (rule.getRuleSource() == ScopeRuleSource.SECURITY_PLATFORM) {
      return null;
    }
    ScopeRuleSnapshot launch = ScopeSnapshotService.withoutHidden(rule.getSnapshotStart(), hidden);
    ScopeRuleSnapshot end = ScopeSnapshotService.withoutHidden(rule.getSnapshotEnd(), hidden);
    return WorkflowScopeRuleOutput.builder()
        .id(rule.getId())
        .selectedMode(rule.getSelectedMode())
        .ruleSource(rule.getRuleSource())
        .ruleValue(rule.getRuleValue())
        // The template-time label snapshot keeps its own meaning (deleted-asset fallback for
        // draft / scenario rules); the launch-time display label is snapshotStartLabel below.
        .ruleValueLabel(rule.getRuleValueLabel())
        // null for draft / scenario / pre-ADR-006 rules (no launch snapshot); front resolves live.
        .status(scopeSnapshotService.computeStatus(rule, hidden))
        .snapshotStartLabel(displayLabel(rule, launch))
        .snapshotStartAssets(toAssetSnapshotOutputs(launch))
        .snapshotEndLabel(displayLabel(rule, end))
        .snapshotEndAssets(toAssetSnapshotOutputs(end))
        .build();
  }

  /**
   * Frozen display label of a photo. An audience (TEAM / PLAYER) photo frozen before those sources
   * were resolved to names carries the raw id as label - suppress it so the frontend falls back to
   * live resolution instead of rendering a UUID chip.
   */
  private String displayLabel(WorkflowScopeRule rule, ScopeRuleSnapshot photo) {
    if (photo == null) {
      return null;
    }
    if (ScopeSnapshotService.isDegradedAudiencePhoto(rule, photo)) {
      return null;
    }
    return photo.getLabel();
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
        .value(SensitiveValueMaskingUtils.maskIfNeeded(variable.getType(), variable.getValue()))
        .description(variable.getDescription())
        .build();
  }
}
