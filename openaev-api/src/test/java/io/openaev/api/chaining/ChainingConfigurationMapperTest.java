package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.openaev.api.chaining.dto.ChainingScopeInput;
import io.openaev.api.chaining.dto.ChainingScopeRuleInput;
import io.openaev.database.model.Scope;
import io.openaev.database.model.ScopeRule;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChainingConfigurationMapperTest {

  private final ChainingConfigurationMapper mapper = new ChainingConfigurationMapper();

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
    ChainingScopeRuleInput ruleInput =
        ChainingScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.WHITELIST)
            .ruleSource(source)
            .ruleValue(ruleValue)
            .build();

    ChainingScopeInput scopeInput =
        ChainingScopeInput.builder().scopeRules(List.of(ruleInput)).build();

    Scope scope = mapper.toScope(scopeInput);

    assertNotNull(scope);
    assertEquals(1, scope.getScopeRules().size());
    assertEquals(1, scope.getWhitelist().size());

    ScopeRule mappedRule = scope.getWhitelist().getFirst();
    assertEquals(ScopeRuleSelectedMode.WHITELIST, mappedRule.getSelectedMode());
    assertEquals(expectedType, mappedRule.getValueType());
    assertEquals(scope, mappedRule.getScope());
  }

  @ParameterizedTest(name = "blacklist source={0}, value={1} -> {2}")
  @MethodSource("valueTypeCases")
  void toScopeShouldDetectRuleValueTypeInBlacklist(
      ScopeRuleSource source, String ruleValue, ScopeRuleValueType expectedType) {
    ChainingScopeRuleInput ruleInput =
        ChainingScopeRuleInput.builder()
            .selectedMode(ScopeRuleSelectedMode.BLACKLIST)
            .ruleSource(source)
            .ruleValue(ruleValue)
            .build();

    ChainingScopeInput scopeInput =
        ChainingScopeInput.builder().scopeRules(List.of(ruleInput)).build();

    Scope scope = mapper.toScope(scopeInput);

    assertNotNull(scope);
    assertEquals(1, scope.getScopeRules().size());
    assertEquals(1, scope.getBlacklist().size());

    ScopeRule mappedRule = scope.getBlacklist().getFirst();
    assertEquals(ScopeRuleSelectedMode.BLACKLIST, mappedRule.getSelectedMode());
    assertEquals(expectedType, mappedRule.getValueType());
    assertEquals(scope, mappedRule.getScope());
  }
}
