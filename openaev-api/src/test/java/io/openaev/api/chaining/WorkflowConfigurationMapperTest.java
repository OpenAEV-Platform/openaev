package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.database.model.*;
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
  void shouldDetectRuleValueTypeForWhitelistRule(
      ScopeRuleSource source, String ruleValue, ScopeRuleValueType expectedType) {
    WorkflowScopeRuleInput ruleInput =
        WorkflowScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.WHITELIST)
            .ruleSource(source)
            .ruleValue(ruleValue)
            .build();

    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder().workflowScopeRules(List.of(ruleInput)).build();

    Workflow workflow = Workflow.builder().build();
    mapper.applyInput(input, workflow);

    assertEquals(1, workflow.getWorkflowScopeRules().size());
    assertEquals(1, workflow.getWhitelist().size());

    WorkflowScopeRule mappedRule = workflow.getWhitelist().getFirst();
    assertEquals(ScopeRuleSelectedMode.WHITELIST, mappedRule.getSelectedMode());
    assertEquals(expectedType, mappedRule.getValueType());
    assertSame(workflow, mappedRule.getWorkflow());
  }

  @ParameterizedTest(name = "source={0}, value={1} -> {2}")
  @MethodSource("valueTypeCases")
  void shouldDetectRuleValueTypeForBlacklistRule(
      ScopeRuleSource source, String ruleValue, ScopeRuleValueType expectedType) {
    WorkflowScopeRuleInput ruleInput =
        WorkflowScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.BLACKLIST)
            .ruleSource(source)
            .ruleValue(ruleValue)
            .build();

    WorkflowConfigurationInput input =
        WorkflowConfigurationInput.builder().workflowScopeRules(List.of(ruleInput)).build();

    Workflow workflow = Workflow.builder().build();
    mapper.applyInput(input, workflow);

    assertEquals(1, workflow.getWorkflowScopeRules().size());
    assertEquals(1, workflow.getBlacklist().size());

    WorkflowScopeRule mappedRule = workflow.getBlacklist().getFirst();
    assertEquals(ScopeRuleSelectedMode.BLACKLIST, mappedRule.getSelectedMode());
    assertEquals(expectedType, mappedRule.getValueType());
    assertSame(workflow, mappedRule.getWorkflow());
  }
}
