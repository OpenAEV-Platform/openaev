package io.openaev.utils.fixtures.import_mapper;

import io.openaev.database.model.RuleAttribute;
import java.util.Map;

public class RuleAttributeFixture {

  private RuleAttributeFixture() {}

  public static RuleAttribute createRuleAttribute(String name) {
    return createRuleAttribute(name, null, Map.of());
  }

  public static RuleAttribute createRuleAttribute(String name, String column) {
    return createRuleAttribute(name, column, Map.of());
  }

  public static RuleAttribute createRuleAttribute(
      String name, String column, Map<String, String> additionalConfig) {
    RuleAttribute ruleAttribute = new RuleAttribute();
    ruleAttribute.setName(name);
    ruleAttribute.setColumns(column);
    ruleAttribute.setDefaultValue("");
    ruleAttribute.setAdditionalConfig(additionalConfig);
    return ruleAttribute;
  }
}
