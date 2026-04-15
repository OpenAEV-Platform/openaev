package io.openaev.utils.fixtures;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionKeyType;
import io.openaev.database.model.ConditionType;

public class ConditionFixture {

  public static Condition getDefaultCondition(ConditionKeyType key, String value) {
    Condition condition = new Condition();
    condition.setKeyType(key);
    condition.setValue(value);
    condition.setType(ConditionType.EQ);
    return condition;
  }
}
