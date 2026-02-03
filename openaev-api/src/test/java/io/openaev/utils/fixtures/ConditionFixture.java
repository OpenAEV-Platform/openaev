package io.openaev.utils.fixtures;

import io.openaev.database.model.Condition;
import java.time.Instant;
import java.util.UUID;

public class ConditionFixture {

  public static Condition getDefaultCondition() {
    Condition condition = new Condition();
    condition.setKey("condition-key-" + UUID.randomUUID());
    condition.setValue("condition-value");
    condition.setCreationDate(Instant.now());
    condition.setUpdateDate(Instant.now());
    return condition;
  }
}
