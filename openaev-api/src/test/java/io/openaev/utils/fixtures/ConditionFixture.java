package io.openaev.utils.fixtures;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;

public class ConditionFixture {

  public static Condition getDefaultCondition(String key, String value) {
    Condition condition = new Condition();
    condition.setKey(key);
    condition.setValue(value);
    condition.setType(ConditionType.EQ);
    return condition;
  }
}
