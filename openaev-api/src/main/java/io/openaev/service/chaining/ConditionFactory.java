package io.openaev.service.chaining;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionKeyType;
import io.openaev.database.model.ConditionType;

public class ConditionFactory {
  private static Condition build(
      String key, ConditionType type, ConditionKeyType keyType, String value) {
    return Condition.builder().key(key).type(type).keyType(keyType).value(value).build();
  }

  public static Condition executionOf(Condition source, Object goal) {
    if (source == null || source.getType() == null) {
      throw new IllegalArgumentException("Source conditions must not be null and must have a type");
    }

    return build(
        source.getKey(),
        source.getType(),
        ConditionKeyType.EXECUTION_TIME,
        goal != null ? goal.toString() : null);
  }

  public static Condition dependOn(String stepTemplateId) {
    if (stepTemplateId == null || stepTemplateId.isBlank()) {
      throw new IllegalArgumentException("stepTemplateId must not be null or blank");
    }
    return build(
        stepTemplateId, ConditionType.DEPEND_ON, ConditionKeyType.STEP_TEMPLATE_ID, stepTemplateId);
  }
}
