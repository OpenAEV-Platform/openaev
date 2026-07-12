package io.openaev.utils;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;

public final class WorkflowScopeRuleUtils {

  private WorkflowScopeRuleUtils() {}

  public static boolean isAssetScopeRule(
      ScopeRuleSource ruleSource, ScopeRuleValueType ruleValueType) {
    return ScopeRuleSource.ASSET.equals(ruleSource)
        || ScopeRuleSource.ASSET_GROUP.equals(ruleSource)
        || ScopeRuleValueType.ASSET_ID.equals(ruleValueType)
        || ScopeRuleValueType.ASSET_GROUP_ID.equals(ruleValueType);
  }

  public static boolean isAssetScopeRule(JsonNode ruleNode) {
    String source =
        ruleNode.hasNonNull("workflow_scope_rule_source")
            ? ruleNode.get("workflow_scope_rule_source").asText()
            : null;
    String valueType =
        ruleNode.hasNonNull("workflow_scope_rule_value_type")
            ? ruleNode.get("workflow_scope_rule_value_type").asText()
            : null;
    return ScopeRuleSource.ASSET.name().equals(source)
        || ScopeRuleSource.ASSET_GROUP.name().equals(source)
        || ScopeRuleValueType.ASSET_ID.name().equals(valueType)
        || ScopeRuleValueType.ASSET_GROUP_ID.name().equals(valueType);
  }
}
