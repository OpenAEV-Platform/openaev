package io.openaev.service.chaining;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import java.util.Objects;

public class ConditionFactory {
  private static final String KEY_TYPE_EXECUTION_TIME = "execution_time";
  private static final String KEY_TYPE_STEP_TEMPLATE_ID = "step_template_id";

  private static Condition build(ConditionType type, String keyType, String value) {
    Condition condition = new Condition();
    condition.setType(type);
    condition.setKeyType(keyType);
    condition.setValue(value);
    return condition;
  }

  public static Condition executionOf(Condition source, Object goal) {
    Objects.requireNonNull(source, "source condition must not be null");
    Objects.requireNonNull(source.getType(), "source condition type must not be null");

    return build(
        ConditionType.DEPEND_ON, KEY_TYPE_EXECUTION_TIME, goal != null ? goal.toString() : null);
  }

  public static Condition dependOn(String stepTemplateId) {
    if (stepTemplateId == null || stepTemplateId.isBlank()) {
      throw new IllegalArgumentException("stepTemplateId must not be null or blank");
    }
    return build(ConditionType.DEPEND_ON, KEY_TYPE_STEP_TEMPLATE_ID, stepTemplateId);
  }
}
