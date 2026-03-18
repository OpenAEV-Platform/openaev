package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openaev.api.chaining.dto.WorkflowScopeInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.database.model.*;
import io.openaev.database.model.WorkflowScope;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WorkflowConfigurationMapperTest {

  private final WorkflowConfigurationMapper mapper = new WorkflowConfigurationMapper();

  static Stream<Arguments> valueTypeCases() {
    return Stream.of(
        Arguments.of(ScopeRuleSource.MANUAL, "10.0.0.0/24", ScopeRuleValueType.IP_SUBNET),
        Arguments.of(ScopeRuleSource.MANUAL, "10.0.0.1", ScopeRuleValueType.IP),
        Arguments.of(ScopeRuleSource.MANUAL, "example.org", ScopeRuleValueType.DOMAIN),
        Arguments.of(ScopeRuleSource.ASSET, "any-value", ScopeRuleValueType.ASSET_ID),
        Arguments.of(ScopeRuleSource.ASSET_GROUP, "any-value", ScopeRuleValueType.ASSET_GROUP_ID));
  }

  @ParameterizedTest(name = "source={0}, value={1} -> {2}")
  @MethodSource("valueTypeCases")
  void toScopeShouldDetectRuleValueType(
      ScopeRuleSource source, String ruleValue, ScopeRuleValueType expectedType) {
    WorkflowScopeRuleInput ruleInput =
        WorkflowScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.WHITELIST)
            .ruleSource(source)
            .ruleValue(ruleValue)
            .build();

    WorkflowScopeInput scopeInput =
        WorkflowScopeInput.builder().workflowScopeRules(List.of(ruleInput)).build();

    WorkflowScope workflowScope = mapper.toScope(scopeInput);

    assertNotNull(workflowScope);
    assertEquals(1, workflowScope.getWorkflowScopeRules().size());
    assertEquals(1, workflowScope.getWhitelist().size());

    WorkflowScopeRule mappedRule = workflowScope.getWhitelist().getFirst();
    assertEquals(ScopeRuleSelectedMode.WHITELIST, mappedRule.getSelectedMode());
    assertEquals(expectedType, mappedRule.getValueType());
    assertEquals(workflowScope, mappedRule.getWorkflowScope());
  }

  @ParameterizedTest(name = "blacklist source={0}, value={1} -> {2}")
  @MethodSource("valueTypeCases")
  void toScopeShouldDetectRuleValueTypeInBlacklist(
      ScopeRuleSource source, String ruleValue, ScopeRuleValueType expectedType) {
    WorkflowScopeRuleInput ruleInput =
        WorkflowScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.BLACKLIST)
            .ruleSource(source)
            .ruleValue(ruleValue)
            .build();

    WorkflowScopeInput scopeInput =
        WorkflowScopeInput.builder().workflowScopeRules(List.of(ruleInput)).build();

    WorkflowScope workflowScope = mapper.toScope(scopeInput);

    assertNotNull(workflowScope);
    assertEquals(1, workflowScope.getWorkflowScopeRules().size());
    assertEquals(1, workflowScope.getBlacklist().size());

    WorkflowScopeRule mappedRule = workflowScope.getBlacklist().getFirst();
    assertEquals(ScopeRuleSelectedMode.BLACKLIST, mappedRule.getSelectedMode());
    assertEquals(expectedType, mappedRule.getValueType());
    assertEquals(workflowScope, mappedRule.getWorkflowScope());
  }
}
