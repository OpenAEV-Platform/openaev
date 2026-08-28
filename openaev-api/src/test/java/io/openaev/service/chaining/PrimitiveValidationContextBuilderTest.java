package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ChainingMappedType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PrimitiveValidationContextBuilder Tests")
class PrimitiveValidationContextBuilderTest {

  private final PrimitiveValidationContextBuilder builder = new PrimitiveValidationContextBuilder();

  private static Workflow workflowWithRules(WorkflowScopeRule... rules) {
    Workflow workflow = Workflow.builder().build();
    workflow.setWorkflowScopeRules(new ArrayList<>(List.of(rules)));
    return workflow;
  }

  private static WorkflowScopeRule rule(
      ScopeRuleSelectedMode mode, ScopeRuleValueType type, String value) {
    return WorkflowScopeRule.builder().selectedMode(mode).valueType(type).ruleValue(value).build();
  }

  @Nested
  @DisplayName("Rule collection")
  class RuleCollection {

    @Test
    @DisplayName("should collect allow and deny sets for the mapped primitive types")
    void given_ipMappings_should_collectIpAndSubnetRules() {
      // Arrange
      Workflow workflow =
          workflowWithRules(
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP, "10.0.0.1"),
              rule(ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.IP, "10.0.0.2"),
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP_SUBNET, "10.0.0.0/24"),
              rule(ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.IP_SUBNET, "10.1.0.0/24"));
      Map<String, ChainingMappedType> typeMappings =
          Map.of("ips", ChainingMappedType.primitive(PrimitiveType.IPv4));

      // Act
      PrimitiveValidationContext context = builder.build(typeMappings, workflow);

      // Assert
      assertThat(context.allowlistedIps()).containsExactly("10.0.0.1");
      assertThat(context.denylistedIps()).containsExactly("10.0.0.2");
      assertThat(context.allowlistedSubnets()).containsExactly("10.0.0.0/24");
      assertThat(context.denylistedSubnets()).containsExactly("10.1.0.0/24");
    }

    @Test
    @DisplayName("should skip rule collection for primitive types absent from the mappings")
    void given_unrelatedMappings_should_returnEmptySets() {
      // Arrange
      Workflow workflow =
          workflowWithRules(
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP, "10.0.0.1"),
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.DOMAIN, "example.org"),
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.ASSET_ID, "asset-1"));
      Map<String, ChainingMappedType> typeMappings =
          Map.of("texts", ChainingMappedType.primitive(PrimitiveType.Text));

      // Act
      PrimitiveValidationContext context = builder.build(typeMappings, workflow);

      // Assert
      assertThat(context.allowlistedIps()).isEmpty();
      assertThat(context.allowlistedDomains()).isEmpty();
      assertThat(context.allowlistedAssetIds()).isEmpty();
    }

    @Test
    @DisplayName("should collect asset and asset-group rules when mapped")
    void given_assetMappings_should_collectAssetRules() {
      // Arrange
      Workflow workflow =
          workflowWithRules(
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.ASSET_ID, "asset-1"),
              rule(ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.ASSET_ID, "asset-2"),
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.ASSET_GROUP_ID, "group-1"),
              rule(ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.ASSET_GROUP_ID, "group-2"));
      Map<String, ChainingMappedType> typeMappings =
          Map.of(
              "assets", ChainingMappedType.primitive(PrimitiveType.AssetId),
              "groups", ChainingMappedType.primitive(PrimitiveType.AssetGroupId));

      // Act
      PrimitiveValidationContext context = builder.build(typeMappings, workflow);

      // Assert
      assertThat(context.allowlistedAssetIds()).containsExactly("asset-1");
      assertThat(context.denylistedAssetIds()).containsExactly("asset-2");
      assertThat(context.allowlistedAssetGroupIds()).containsExactly("group-1");
      assertThat(context.denylistedAssetGroupIds()).containsExactly("group-2");
    }
  }

  @Nested
  @DisplayName("Value normalization")
  class ValueNormalization {

    @Test
    @DisplayName("should lowercase domain rules")
    void given_mixedCaseDomainRules_should_normalizeToLowercase() {
      // Arrange
      Workflow workflow =
          workflowWithRules(
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.DOMAIN, "Example.ORG"),
              rule(ScopeRuleSelectedMode.DENYLIST, ScopeRuleValueType.DOMAIN, "BLOCKED.org"));
      Map<String, ChainingMappedType> typeMappings =
          Map.of("domains", ChainingMappedType.primitive(PrimitiveType.Domain));

      // Act
      PrimitiveValidationContext context = builder.build(typeMappings, workflow);

      // Assert
      assertThat(context.allowlistedDomains()).containsExactly("example.org");
      assertThat(context.denylistedDomains()).containsExactly("blocked.org");
    }

    @Test
    @DisplayName("should trim rule values and drop blank or null ones")
    void given_blankAndPaddedRuleValues_should_trimAndFilter() {
      // Arrange
      Workflow workflow =
          workflowWithRules(
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP, "  10.0.0.1  "),
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP, "   "),
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP, null));
      Map<String, ChainingMappedType> typeMappings =
          Map.of("ips", ChainingMappedType.primitive(PrimitiveType.IPv4));

      // Act
      PrimitiveValidationContext context = builder.build(typeMappings, workflow);

      // Assert
      assertThat(context.allowlistedIps()).containsExactly("10.0.0.1");
    }

    @Test
    @DisplayName("should ignore null mapped types in the mappings")
    void given_nullMappedType_should_ignoreIt() {
      // Arrange
      Workflow workflow =
          workflowWithRules(
              rule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.IP, "10.0.0.1"));
      Map<String, ChainingMappedType> typeMappings = new java.util.HashMap<>();
      typeMappings.put("unmapped", null);

      // Act
      PrimitiveValidationContext context = builder.build(typeMappings, workflow);

      // Assert
      assertThat(context.allowlistedIps()).isEmpty();
    }
  }
}
