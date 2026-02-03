package io.openaev.utils.fixtures;

import io.openaev.database.model.CONDITION_TYPE;
import io.openaev.database.model.Condition;

public class ConditionFixture {

  public static Condition getDefaultCondition(String key, String value) {
    Condition condition = new Condition();
    condition.setKey(key);
    condition.setValue(value);
    condition.setType(CONDITION_TYPE.EQ);
    return condition;
  }
}
