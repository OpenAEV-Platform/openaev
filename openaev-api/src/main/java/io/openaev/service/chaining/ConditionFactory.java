package io.openaev.service.chaining;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionKeyType;
import io.openaev.database.model.ConditionType;
import java.util.Objects;

public class ConditionFactory {
  private static Condition build(
      String key, ConditionType type, ConditionKeyType keyType, String value) {
    return Condition.builder().key(key).type(type).keyType(keyType).value(value).build();
  }

  public static Condition executionOf(Condition source, Object goal) {
    Objects.requireNonNull(source, "source condition must not be null");
    Objects.requireNonNull(source.getType(), "source condition type must not be null");

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
