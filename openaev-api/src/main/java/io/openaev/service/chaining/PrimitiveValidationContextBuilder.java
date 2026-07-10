package io.openaev.service.chaining;

import io.openaev.database.model.*;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrimitiveValidationContextBuilder {

  /**
   * Builds a precomputed context used to validate primitive values during workflow-state ingestion.
   *
   * @param typeMappings resolved output-to-primitive mapping for the current sync
   * @param workflowRun current workflow execution
   * @return immutable context with allow/deny sets required by primitive validation
   */
  public PrimitiveValidationContext build(
      Map<String, ChainingMappedType> typeMappings, Workflow workflowRun) {
    boolean needsAssetIdValidation =
        requiresPrimitiveValidation(typeMappings, PrimitiveType.AssetId);
    boolean needsAssetGroupValidation =
        requiresPrimitiveValidation(typeMappings, PrimitiveType.AssetGroupId);
    boolean needsIpValidation =
        requiresPrimitiveValidation(typeMappings, PrimitiveType.IPv4, PrimitiveType.IPv6);
    boolean needsSubnetValidation =
        requiresPrimitiveValidation(typeMappings, PrimitiveType.IpSubnet);
    boolean needsDomainValidation = requiresPrimitiveValidation(typeMappings, PrimitiveType.Domain);

    Set<String> allowlistedAssetIds =
        needsAssetIdValidation
            ? collectRuleValues(
                workflowRun, ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.ASSET_ID)
            : Collections.emptySet();
    Set<String> denylistedAssetIds =
        needsAssetIdValidation
            ? collectRuleValues(
                workflowRun, ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.ASSET_ID)
            : Collections.emptySet();

    Set<String> allowlistedAssetGroupIds =
        needsAssetGroupValidation
            ? collectRuleValues(
                workflowRun, ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.ASSET_GROUP_ID)
            : Collections.emptySet();
    Set<String> denylistedAssetGroupIds =
        needsAssetGroupValidation
            ? collectRuleValues(
                workflowRun, ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.ASSET_GROUP_ID)
            : Collections.emptySet();

    Set<String> allowlistedIps =
        needsIpValidation || needsSubnetValidation
            ? collectRuleValues(workflowRun, ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP)
            : Collections.emptySet();
    Set<String> denylistedIps =
        needsIpValidation || needsSubnetValidation
            ? collectRuleValues(workflowRun, ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.IP)
            : Collections.emptySet();

    Set<String> allowlistedSubnets =
        needsIpValidation || needsSubnetValidation
            ? collectRuleValues(
                workflowRun, ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP_SUBNET)
            : Collections.emptySet();
    Set<String> denylistedSubnets =
        needsIpValidation || needsSubnetValidation
            ? collectRuleValues(
                workflowRun, ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.IP_SUBNET)
            : Collections.emptySet();

    Set<String> allowlistedDomains =
        needsDomainValidation
            ? normalizeDomains(
                collectRuleValues(
                    workflowRun, ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.DOMAIN))
            : Collections.emptySet();
    Set<String> denylistedDomains =
        needsDomainValidation
            ? normalizeDomains(
                collectRuleValues(
                    workflowRun, ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.DOMAIN))
            : Collections.emptySet();

    return new PrimitiveValidationContext(
        allowlistedAssetGroupIds,
        allowlistedAssetIds,
        allowlistedDomains,
        allowlistedIps,
        allowlistedSubnets,
        denylistedAssetGroupIds,
        denylistedAssetIds,
        denylistedDomains,
        denylistedIps,
        denylistedSubnets);
  }

  private boolean requiresPrimitiveValidation(
      Map<String, ChainingMappedType> typeMappings, PrimitiveType... primitiveTypes) {
    Set<PrimitiveType> targetTypes = Set.of(primitiveTypes);
    return typeMappings.values().stream()
        .filter(Objects::nonNull)
        .map(ChainingMappedType::primitiveTypes)
        .filter(Objects::nonNull)
        .anyMatch(types -> types.stream().anyMatch(targetTypes::contains));
  }

  private Set<String> collectRuleValues(
      Workflow workflowRun, ScopeRuleSelectedMode selectedMode, ScopeRuleValueType valueType) {
    return workflowRun.getWorkflowScopeRules().stream()
        .filter(rule -> rule.getSelectedMode() == selectedMode)
        .filter(rule -> rule.getValueType() == valueType)
        .map(WorkflowScopeRule::getRuleValue)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.toSet());
  }

  private Set<String> normalizeDomains(Set<String> domains) {
    return domains.stream().map(d -> d.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
  }
}
